package net.osmand.plus.development;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import net.osmand.plus.ContextMenuAdapter;
import net.osmand.plus.ContextMenuItem;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.Version;
import net.osmand.plus.activities.ContributionVersionActivity;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.dashboard.tools.DashFragmentData;
import net.osmand.plus.firstusage.FirstUsageWizardFragment;
import net.osmand.plus.server.ServerFragment;
import net.osmand.plus.settings.fragments.BaseSettingsFragment;
import net.osmand.plus.views.layers.MapInfoLayer;
import net.osmand.plus.views.OsmandMapLayer.DrawSettings;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.mapwidgets.widgets.TextInfoWidget;

import static net.osmand.aidlapi.OsmAndCustomizationConstants.DRAWER_BUILDS_ID;
import static net.osmand.aidlapi.OsmAndCustomizationConstants.DRAWER_WEBSERVER_ID;

public class OsmandDevelopmentPlugin extends OsmandPlugin {

	private static final String ID = "osmand.development";

	public OsmandDevelopmentPlugin(OsmandApplication app) {
		super(app);
		//ApplicationMode.regWidgetVisibility("fps", new ApplicationMode[0]);
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public String getDescription() {
		return app.getString(R.string.osmand_development_plugin_description);
	}

	@Override
	public String getName() {
		return app.getString(R.string.debugging_and_development);
	}

	@Override
	public String getHelpFileName() {
		return "feature_articles/development_plugin.html";
	}

	@Override
	public void registerLayers(MapActivity activity) {
		registerWidget(activity);
	}

	@Override
	public void registerOptionsMenuItems(final MapActivity mapActivity, ContextMenuAdapter helper) {
		if (Version.isDeveloperVersion(mapActivity.getMyApplication())) {
			helper.addItem(new ContextMenuItem.ItemBuilder()
					.setId(DRAWER_BUILDS_ID)
					.setTitleId(R.string.version_settings, mapActivity)
					.setIcon(R.drawable.ic_action_apk)
					.setListener(new ContextMenuAdapter.ItemClickListener() {
						@Override
						public boolean onContextMenuClick(ArrayAdapter<ContextMenuItem> adapter, int itemId, int pos, boolean isChecked, int[] viewCoordinates) {
							final Intent mapIntent = new Intent(mapActivity, ContributionVersionActivity.class);
							mapActivity.startActivityForResult(mapIntent, 0);
							return true;
						}
					}).createItem());

			helper.addItem(new ContextMenuItem.ItemBuilder().setTitleId(R.string.web_server, mapActivity)
					.setId(DRAWER_WEBSERVER_ID)
					.setIcon(R.drawable.mm_shop_computer)
					.setListener(new ContextMenuAdapter.ItemClickListener() {
						@Override
						public boolean onContextMenuClick(ArrayAdapter<ContextMenuItem> adapter, int itemId, int pos, boolean isChecked, int[] viewCoordinates) {
							Fragment fragment = new ServerFragment();
							showFragment(mapActivity, fragment);
							return true;
						}
					}).createItem());
		}
	}

	private static void showFragment(FragmentActivity activity, Fragment fragment) {
		if (activity != null) {
			activity.getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.fragmentContainer, fragment, ServerFragment.TAG)
					.commitAllowingStateLoss();
		}
	}
	@Override
	public void updateLayers(OsmandMapTileView mapView, MapActivity activity) {
		if (isActive()) {
			registerWidget(activity);
		} else {
			MapInfoLayer mapInfoLayer = activity.getMapLayers().getMapInfoLayer();
			if (mapInfoLayer != null && mapInfoLayer.getSideWidget(FPSTextInfoWidget.class) != null) {
				mapInfoLayer.removeSideWidget(mapInfoLayer.getSideWidget(FPSTextInfoWidget.class));
				mapInfoLayer.recreateControls();
			}
		}
	}

	public static class FPSTextInfoWidget extends TextInfoWidget {

		private OsmandMapTileView mv;

		public FPSTextInfoWidget(OsmandMapTileView mv, Activity activity) {
			super(activity);
			this.mv = mv;
		}

		@Override
		public boolean updateInfo(DrawSettings drawSettings) {
			if (!mv.isMeasureFPS()) {
				mv.setMeasureFPS(true);
			}
			setText("", Integer.toString((int) mv.getFPS()) + "/"
					+ Integer.toString((int) mv.getSecondaryFPS())
					+ " FPS");
			return true;
		}
	}


	private void registerWidget(MapActivity activity) {
		MapInfoLayer mapInfoLayer = activity.getMapLayers().getMapInfoLayer();
		final OsmandMapTileView mv = activity.getMapView();
		if (mapInfoLayer != null && mapInfoLayer.getSideWidget(FPSTextInfoWidget.class) == null) {
			FPSTextInfoWidget fps = new FPSTextInfoWidget(mv, activity);
			mapInfoLayer.registerSideWidget(fps, R.drawable.ic_action_fps,
					R.string.map_widget_fps_info, "fps", false, 50);
			mapInfoLayer.recreateControls();
		}
	}

	@Override
	public Class<? extends Activity> getSettingsActivity() {
		return SettingsDevelopmentActivity.class;
	}

	@Override
	public Class<? extends BaseSettingsFragment> getSettingsFragment() {
		return DevelopmentSettingsFragment.class;
	}

	@Override
	public int getLogoResourceId() {
		return R.drawable.ic_action_laptop;
	}

	@Override
	public Drawable getAssetResourceImage() {
		return app.getUIUtilities().getIcon(R.drawable.osmand_development);
	}

	@Override
	public DashFragmentData getCardFragment() {
		return DashSimulateFragment.FRAGMENT_DATA;
	}
}