package net.osmand.plus.inapp;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.AndroidUtils;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.inapp.util.BillingManager;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.util.Algorithms;

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InAppPurchaseHelperImpl extends InAppPurchaseHelper {

	// The helper object
	private BillingManager billingManager;
	private List<SkuDetails> skuDetailsList;


	public InAppPurchaseHelperImpl(OsmandApplication ctx) {
		super(ctx);
	}

	protected void execImpl(@NonNull final InAppPurchaseTaskType taskType, @NonNull final InAppRunnable runnable) {
		billingManager = new BillingManager(ctx, BASE64_ENCODED_PUBLIC_KEY, new BillingManager.BillingUpdatesListener() {

			@Override
			public void onBillingClientSetupFinished() {
				logDebug("Setup finished.");

				BillingManager billingManager = getBillingManager();
				// Have we been disposed of in the meantime? If so, quit.
				if (billingManager == null) {
					stop(true);
					return;
				}

				if (!billingManager.isServiceConnected()) {
					// Oh noes, there was a problem.
					//complain("Problem setting up in-app billing: " + result);
					notifyError(taskType, billingManager.getBillingClientResponseMessage());
					stop(true);
					return;
				}

				processingTask = !runnable.run(InAppPurchaseHelperImpl.this);
			}

			@Override
			public void onConsumeFinished(String token, BillingResult billingResult) {
			}

			@Override
			public void onPurchasesUpdated(final List<Purchase> purchases) {

				BillingManager billingManager = getBillingManager();
				// Have we been disposed of in the meantime? If so, quit.
				if (billingManager == null) {
					stop(true);
					return;
				}

				if (activeTask == InAppPurchaseTaskType.REQUEST_INVENTORY) {
					List<String> skuInApps = new ArrayList<>();
					for (InAppPurchases.InAppPurchase purchase : getInAppPurchases().getAllInAppPurchases(false)) {
						skuInApps.add(purchase.getSku());
					}
					for (Purchase p : purchases) {
						skuInApps.add(p.getSku());
					}
					billingManager.querySkuDetailsAsync(BillingClient.SkuType.INAPP, skuInApps, new SkuDetailsResponseListener() {
						@Override
						public void onSkuDetailsResponse(BillingResult billingResult, final List<SkuDetails> skuDetailsListInApps) {
							// Is it a failure?
							if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
								logError("Failed to query inapps sku details: " + billingResult.getResponseCode());
								notifyError(InAppPurchaseTaskType.REQUEST_INVENTORY, billingResult.getDebugMessage());
								stop(true);
								return;
							}

							List<String> skuSubscriptions = new ArrayList<>();
							for (InAppPurchases.InAppSubscription subscription : getInAppPurchases().getAllInAppSubscriptions()) {
								skuSubscriptions.add(subscription.getSku());
							}
							for (Purchase p : purchases) {
								skuSubscriptions.add(p.getSku());
							}

							BillingManager billingManager = getBillingManager();
							// Have we been disposed of in the meantime? If so, quit.
							if (billingManager == null) {
								stop(true);
								return;
							}

							billingManager.querySkuDetailsAsync(BillingClient.SkuType.SUBS, skuSubscriptions, new SkuDetailsResponseListener() {
								@Override
								public void onSkuDetailsResponse(BillingResult billingResult, final List<SkuDetails> skuDetailsListSubscriptions) {
									// Is it a failure?
									if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
										logError("Failed to query subscriptipons sku details: " + billingResult.getResponseCode());
										notifyError(InAppPurchaseTaskType.REQUEST_INVENTORY, billingResult.getDebugMessage());
										stop(true);
										return;
									}

									List<SkuDetails> skuDetailsList = new ArrayList<>(skuDetailsListInApps);
									skuDetailsList.addAll(skuDetailsListSubscriptions);
									InAppPurchaseHelperImpl.this.skuDetailsList = skuDetailsList;

									mSkuDetailsResponseListener.onSkuDetailsResponse(billingResult, skuDetailsList);
								}
							});
						}
					});
				}
				for (Purchase purchase : purchases) {
					if (!purchase.isAcknowledged()) {
						onPurchaseFinished(purchase);
					}
				}
			}

			@Override
			public void onPurchaseCanceled() {
				stop(true);
			}
		});
	}

	@Override
	public void purchaseFullVersion(Activity activity) throws UnsupportedOperationException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void purchaseDepthContours(Activity activity) throws UnsupportedOperationException {
		throw new UnsupportedOperationException();
	}

	@Nullable
	private SkuDetails getSkuDetails(@NonNull String sku) {
		List<SkuDetails> skuDetailsList = this.skuDetailsList;
		if (skuDetailsList != null) {
			for (SkuDetails details : skuDetailsList) {
				if (details.getSku().equals(sku)) {
					return details;
				}
			}
		}
		return null;
	}

	private boolean hasDetails(@NonNull String sku) {
		return getSkuDetails(sku) != null;
	}

	@Nullable
	private Purchase getPurchase(@NonNull String sku) {
		BillingManager billingManager = getBillingManager();
		if (billingManager != null) {
			List<Purchase> purchases = billingManager.getPurchases();
			if (purchases != null) {
				for (Purchase p : purchases) {
					if (p.getSku().equals(sku)) {
						return p;
					}
				}
			}
		}
		return null;
	}

	// Listener that's called when we finish querying the items and subscriptions we own
	private SkuDetailsResponseListener mSkuDetailsResponseListener = new SkuDetailsResponseListener() {

		@NonNull
		private List<String> getAllOwnedSubscriptionSkus() {
			List<String> result = new ArrayList<>();
			BillingManager billingManager = getBillingManager();
			if (billingManager != null) {
				for (Purchase p : billingManager.getPurchases()) {
					if (getInAppPurchases().getInAppSubscriptionBySku(p.getSku()) != null) {
						result.add(p.getSku());
					}
				}
			}
			return result;
		}

		@Override
		public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList) {

			logDebug("Query sku details finished.");

			// Have we been disposed of in the meantime? If so, quit.
			if (getBillingManager() == null) {
				stop(true);
				return;
			}

			// Is it a failure?
			if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
				logError("Failed to query inventory: " + billingResult.getResponseCode());
				notifyError(InAppPurchaseTaskType.REQUEST_INVENTORY, billingResult.getDebugMessage());
				stop(true);
				return;
			}

			logDebug("Query sku details was successful.");

			/*
			 * Check for items we own. Notice that for each purchase, we check
			 * the developer payload to see if it's correct! See
			 * verifyDeveloperPayload().
			 */

			List<String> allOwnedSubscriptionSkus = getAllOwnedSubscriptionSkus();
			for (InAppPurchases.InAppSubscription s : getLiveUpdates().getAllSubscriptions()) {
				if (hasDetails(s.getSku())) {
					Purchase purchase = getPurchase(s.getSku());
					SkuDetails liveUpdatesDetails = getSkuDetails(s.getSku());
					if (liveUpdatesDetails != null) {
						fetchInAppPurchase(s, liveUpdatesDetails, purchase);
					}
					allOwnedSubscriptionSkus.remove(s.getSku());
				}
			}
			for (String sku : allOwnedSubscriptionSkus) {
				Purchase purchase = getPurchase(sku);
				SkuDetails liveUpdatesDetails = getSkuDetails(sku);
				if (liveUpdatesDetails != null) {
					InAppPurchases.InAppSubscription s = getLiveUpdates().upgradeSubscription(sku);
					if (s == null) {
						s = new InAppPurchases.InAppPurchaseLiveUpdatesOldSubscription(liveUpdatesDetails);
					}
					fetchInAppPurchase(s, liveUpdatesDetails, purchase);
				}
			}

			InAppPurchases.InAppPurchase fullVersion = getFullVersion();
			if (hasDetails(fullVersion.getSku())) {
				Purchase purchase = getPurchase(fullVersion.getSku());
				SkuDetails fullPriceDetails = getSkuDetails(fullVersion.getSku());
				if (fullPriceDetails != null) {
					fetchInAppPurchase(fullVersion, fullPriceDetails, purchase);
				}
			}

			InAppPurchases.InAppPurchase depthContours = getDepthContours();
			if (hasDetails(depthContours.getSku())) {
				Purchase purchase = getPurchase(depthContours.getSku());
				SkuDetails depthContoursDetails = getSkuDetails(depthContours.getSku());
				if (depthContoursDetails != null) {
					fetchInAppPurchase(depthContours, depthContoursDetails, purchase);
				}
			}

			InAppPurchases.InAppPurchase contourLines = getContourLines();
			if (hasDetails(contourLines.getSku())) {
				Purchase purchase = getPurchase(contourLines.getSku());
				SkuDetails contourLinesDetails = getSkuDetails(contourLines.getSku());
				if (contourLinesDetails != null) {
					fetchInAppPurchase(contourLines, contourLinesDetails, purchase);
				}
			}

			Purchase fullVersionPurchase = getPurchase(fullVersion.getSku());
			boolean fullVersionPurchased = fullVersionPurchase != null;
			if (fullVersionPurchased) {
				ctx.getSettings().FULL_VERSION_PURCHASED.set(true);
			}

			Purchase depthContoursPurchase = getPurchase(depthContours.getSku());
			boolean depthContoursPurchased = depthContoursPurchase != null;
			if (depthContoursPurchased) {
				ctx.getSettings().DEPTH_CONTOURS_PURCHASED.set(true);
			}

			// Do we have the live updates?
			boolean subscribedToLiveUpdates = false;
			List<Purchase> liveUpdatesPurchases = new ArrayList<>();
			for (InAppPurchases.InAppPurchase p : getLiveUpdates().getAllSubscriptions()) {
				Purchase purchase = getPurchase(p.getSku());
				if (purchase != null) {
					liveUpdatesPurchases.add(purchase);
					if (!subscribedToLiveUpdates) {
						subscribedToLiveUpdates = true;
					}
				}
			}
			OsmandSettings.OsmandPreference<Long> subscriptionCancelledTime = ctx.getSettings().LIVE_UPDATES_PURCHASE_CANCELLED_TIME;
			if (!subscribedToLiveUpdates && ctx.getSettings().LIVE_UPDATES_PURCHASED.get()) {
				if (subscriptionCancelledTime.get() == 0) {
					subscriptionCancelledTime.set(System.currentTimeMillis());
					ctx.getSettings().LIVE_UPDATES_PURCHASE_CANCELLED_FIRST_DLG_SHOWN.set(false);
					ctx.getSettings().LIVE_UPDATES_PURCHASE_CANCELLED_SECOND_DLG_SHOWN.set(false);
				} else if (System.currentTimeMillis() - subscriptionCancelledTime.get() > SUBSCRIPTION_HOLDING_TIME_MSEC) {
					ctx.getSettings().LIVE_UPDATES_PURCHASED.set(false);
					if (!isDepthContoursPurchased(ctx)) {
						ctx.getSettings().getCustomRenderBooleanProperty("depthContours").set(false);
					}
				}
			} else if (subscribedToLiveUpdates) {
				subscriptionCancelledTime.set(0L);
				ctx.getSettings().LIVE_UPDATES_PURCHASED.set(true);
			}

			lastValidationCheckTime = System.currentTimeMillis();
			logDebug("User " + (subscribedToLiveUpdates ? "HAS" : "DOES NOT HAVE")
					+ " live updates purchased.");

			OsmandSettings settings = ctx.getSettings();
			settings.INAPPS_READ.set(true);

			List<Purchase> tokensToSend = new ArrayList<>();
			if (liveUpdatesPurchases.size() > 0) {
				List<String> tokensSent = Arrays.asList(settings.BILLING_PURCHASE_TOKENS_SENT.get().split(";"));
				for (Purchase purchase : liveUpdatesPurchases) {
					if ((Algorithms.isEmpty(settings.BILLING_USER_ID.get()) || Algorithms.isEmpty(settings.BILLING_USER_TOKEN.get()))
							&& !Algorithms.isEmpty(purchase.getDeveloperPayload())) {
						String payload = purchase.getDeveloperPayload();
						if (!Algorithms.isEmpty(payload)) {
							String[] arr = payload.split(" ");
							if (arr.length > 0) {
								settings.BILLING_USER_ID.set(arr[0]);
							}
							if (arr.length > 1) {
								token = arr[1];
								settings.BILLING_USER_TOKEN.set(token);
							}
						}
					}
					if (!tokensSent.contains(purchase.getSku())) {
						tokensToSend.add(purchase);
					}
				}
			}
			List<PurchaseInfo> purchaseInfoList = new ArrayList<>();
			for (Purchase purchase : tokensToSend) {
				purchaseInfoList.add(getPurchaseInfo(purchase));
			}
			onSkuDetailsResponseDone(purchaseInfoList);
		}
	};

	private PurchaseInfo getPurchaseInfo(Purchase purchase) {
		return new PurchaseInfo(purchase.getSku(), purchase.getOrderId(), purchase.getPurchaseToken());
	}

	private void fetchInAppPurchase(@NonNull InAppPurchases.InAppPurchase inAppPurchase, @NonNull SkuDetails skuDetails, @Nullable Purchase purchase) {
		if (purchase != null) {
			inAppPurchase.setPurchaseState(InAppPurchases.InAppPurchase.PurchaseState.PURCHASED);
			inAppPurchase.setPurchaseTime(purchase.getPurchaseTime());
		} else {
			inAppPurchase.setPurchaseState(InAppPurchases.InAppPurchase.PurchaseState.NOT_PURCHASED);
		}
		inAppPurchase.setPrice(skuDetails.getPrice());
		inAppPurchase.setPriceCurrencyCode(skuDetails.getPriceCurrencyCode());
		if (skuDetails.getPriceAmountMicros() > 0) {
			inAppPurchase.setPriceValue(skuDetails.getPriceAmountMicros() / 1000000d);
		}
		String subscriptionPeriod = skuDetails.getSubscriptionPeriod();
		if (!Algorithms.isEmpty(subscriptionPeriod)) {
			if (inAppPurchase instanceof InAppPurchases.InAppSubscription) {
				try {
					((InAppPurchases.InAppSubscription) inAppPurchase).setSubscriptionPeriodString(subscriptionPeriod);
				} catch (ParseException e) {
					LOG.error(e);
				}
			}
		}
		if (inAppPurchase instanceof InAppPurchases.InAppSubscription) {
			String introductoryPrice = skuDetails.getIntroductoryPrice();
			String introductoryPricePeriod = skuDetails.getIntroductoryPricePeriod();
			String introductoryPriceCycles = skuDetails.getIntroductoryPriceCycles();
			long introductoryPriceAmountMicros = skuDetails.getIntroductoryPriceAmountMicros();
			if (!Algorithms.isEmpty(introductoryPrice)) {
				InAppPurchases.InAppSubscription s = (InAppPurchases.InAppSubscription) inAppPurchase;
				try {
					s.setIntroductoryInfo(new InAppPurchases.InAppSubscriptionIntroductoryInfo(s, introductoryPrice,
							introductoryPriceAmountMicros, introductoryPricePeriod, introductoryPriceCycles));
				} catch (ParseException e) {
					LOG.error(e);
				}
			}
		}
	}

	protected InAppRunnable getPurchaseLiveUpdatesCommand(final WeakReference<Activity> activity, final String sku, final String payload) {
		return new InAppRunnable() {
			@Override
			public boolean run(InAppPurchaseHelper helper) {
				try {
					Activity a = activity.get();
					SkuDetails skuDetails = getSkuDetails(sku);
					if (AndroidUtils.isActivityNotDestroyed(a) && skuDetails != null) {
						BillingManager billingManager = getBillingManager();
						if (billingManager != null) {
							billingManager.setPayload(payload);
							billingManager.initiatePurchaseFlow(a, skuDetails);
						} else {
							throw new IllegalStateException("BillingManager disposed");
						}
						return false;
					} else {
						stop(true);
					}
				} catch (Exception e) {
					logError("launchPurchaseFlow Error", e);
					stop(true);
				}
				return true;
			}
		};
	}

	protected InAppRunnable getRequestInventoryCommand() {
		return new InAppRunnable() {
			@Override
			public boolean run(InAppPurchaseHelper helper) {
				logDebug("Setup successful. Querying inventory.");
				try {
					BillingManager billingManager = getBillingManager();
					if (billingManager != null) {
						billingManager.queryPurchases();
					} else {
						throw new IllegalStateException("BillingManager disposed");
					}
					return false;
				} catch (Exception e) {
					logError("queryInventoryAsync Error", e);
					notifyDismissProgress(InAppPurchaseTaskType.REQUEST_INVENTORY);
					stop(true);
				}
				return true;
			}
		};
	}

	// Call when a purchase is finished
	private void onPurchaseFinished(Purchase purchase) {
		logDebug("Purchase finished: " + purchase);

		// if we were disposed of in the meantime, quit.
		if (getBillingManager() == null) {
			stop(true);
			return;
		}

		onPurchaseDone(getPurchaseInfo(purchase));
	}

	@Override
	protected boolean isBillingManagerExists() {
		return getBillingManager() != null;
	}

	@Override
	protected void destroyBillingManager() {
		BillingManager billingManager = getBillingManager();
		if (billingManager != null) {
			billingManager.destroy();
			this.billingManager = null;
		}
	}
}
