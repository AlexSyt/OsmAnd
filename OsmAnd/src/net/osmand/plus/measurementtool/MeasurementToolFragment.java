package net.osmand.plus.measurementtool;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.snackbar.Snackbar;

import net.osmand.AndroidUtils;
import net.osmand.FileUtils;
import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.IndexConstants;
import net.osmand.LocationsHolder;
import net.osmand.data.LatLon;
import net.osmand.data.QuadRect;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.Version;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.MapActivityActions;
import net.osmand.plus.activities.TrackActivity;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.base.ContextMenuFragment.MenuState;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.measurementtool.GpxApproximationFragment.GpxApproximationFragmentListener;
import net.osmand.plus.measurementtool.GpxData.ActionType;
import net.osmand.plus.measurementtool.OptionsBottomSheetDialogFragment.OptionsFragmentListener;
import net.osmand.plus.measurementtool.RouteBetweenPointsBottomSheetDialogFragment.RouteBetweenPointsDialogMode;
import net.osmand.plus.measurementtool.RouteBetweenPointsBottomSheetDialogFragment.RouteBetweenPointsDialogType;
import net.osmand.plus.measurementtool.RouteBetweenPointsBottomSheetDialogFragment.RouteBetweenPointsFragmentListener;
import net.osmand.plus.measurementtool.SelectedPointBottomSheetDialogFragment.SelectedPointFragmentListener;
import net.osmand.plus.measurementtool.command.AddPointCommand;
import net.osmand.plus.measurementtool.command.ApplyGpxApproximationCommand;
import net.osmand.plus.measurementtool.command.ChangeRouteModeCommand;
import net.osmand.plus.measurementtool.command.ChangeRouteModeCommand.ChangeRouteType;
import net.osmand.plus.measurementtool.command.ClearPointsCommand;
import net.osmand.plus.measurementtool.command.MovePointCommand;
import net.osmand.plus.measurementtool.command.RemovePointCommand;
import net.osmand.plus.measurementtool.command.ReversePointsCommand;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopToolbarController;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopToolbarControllerType;
import net.osmand.plus.views.mapwidgets.MapInfoWidgetsFactory.TopToolbarView;
import net.osmand.router.RoutePlannerFrontEnd.GpxRouteApproximation;
import net.osmand.util.Algorithms;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static net.osmand.IndexConstants.GPX_FILE_EXT;
import static net.osmand.plus.UiUtilities.CustomRadioButtonType.LEFT;
import static net.osmand.plus.UiUtilities.CustomRadioButtonType.RIGHT;
import static net.osmand.plus.measurementtool.MeasurementEditingContext.CalculationMode;
import static net.osmand.plus.measurementtool.MeasurementEditingContext.SnapToRoadProgressListener;
import static net.osmand.plus.measurementtool.SaveAsNewTrackBottomSheetDialogFragment.SaveAsNewTrackFragmentListener;
import static net.osmand.plus.measurementtool.SelectFileBottomSheet.Mode.ADD_TO_TRACK;
import static net.osmand.plus.measurementtool.SelectFileBottomSheet.Mode.OPEN_TRACK;
import static net.osmand.plus.measurementtool.SelectFileBottomSheet.SelectFileListener;
import static net.osmand.plus.measurementtool.StartPlanRouteBottomSheet.StartPlanRouteListener;
import static net.osmand.plus.measurementtool.command.ClearPointsCommand.ClearCommandMode;
import static net.osmand.plus.measurementtool.command.ClearPointsCommand.ClearCommandMode.AFTER;
import static net.osmand.plus.measurementtool.command.ClearPointsCommand.ClearCommandMode.ALL;
import static net.osmand.plus.measurementtool.command.ClearPointsCommand.ClearCommandMode.BEFORE;
import static net.osmand.GPXUtilities.Track;

public class MeasurementToolFragment extends BaseOsmAndFragment implements RouteBetweenPointsFragmentListener,
		OptionsFragmentListener, GpxApproximationFragmentListener, SelectedPointFragmentListener,
		SaveAsNewTrackFragmentListener {

	public static final String TAG = MeasurementToolFragment.class.getSimpleName();

	private String previousToolBarTitle = "";
	private MeasurementToolBarController toolBarController;
	private TextView distanceTv;
	private TextView pointsTv;
	private TextView distanceToCenterTv;
	private String pointsSt;
	private View additionalInfoContainer;
	private LinearLayout customRadioButton;
	private View mainView;
	private ImageView upDownBtn;
	private ImageView undoBtn;
	private ImageView redoBtn;
	private ImageView mainIcon;
	private Snackbar snackbar;
	private String fileName;

	private boolean wasCollapseButtonVisible;
	private boolean progressBarVisible;
	private boolean additionalInfoOpened;
	private boolean planRouteMode = false;
	private boolean directionMode = false;
	private boolean portrait;
	private boolean nightMode;
	private int cachedMapPosition;
	private AdditionalInfoType currentAdditionalInfoType;

	private MeasurementEditingContext editingCtx = new MeasurementEditingContext();

	private LatLon initialPoint;

	public enum SaveType {
		ROUTE_POINT,
		LINE
	}

	private enum AdditionalInfoType {
		POINTS(MtPointsFragment.TAG),
		GRAPH(MtGraphFragment.TAG);

		String fragmentName;

		AdditionalInfoType(String fragmentName) {
			this.fragmentName = fragmentName;
		}
	}

	private enum SaveAction {
		SHOW_SNACK_BAR_AND_CLOSE,
		SHOW_TOAST,
		SHOW_IS_SAVED_FRAGMENT
	}

	private void setEditingCtx(MeasurementEditingContext editingCtx) {
		this.editingCtx = editingCtx;
	}

	private void setInitialPoint(LatLon initialPoint) {
		this.initialPoint = initialPoint;
	}

	private void setPlanRouteMode(boolean planRouteMode) {
		this.planRouteMode = planRouteMode;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
	                         @Nullable Bundle savedInstanceState) {
		final MapActivity mapActivity = (MapActivity) getActivity();
		if (mapActivity == null) {
			return null;
		}
		final MeasurementToolLayer measurementLayer = mapActivity.getMapLayers().getMeasurementToolLayer();

		editingCtx.setApplication(mapActivity.getMyApplication());
		editingCtx.setProgressListener(new SnapToRoadProgressListener() {
			@Override
			public void showProgressBar() {
				MeasurementToolFragment.this.showProgressBar();
			}

			@Override
			public void updateProgress(int progress) {
				((ProgressBar) mainView.findViewById(R.id.snap_to_road_progress_bar)).setProgress(progress);
			}

			@Override
			public void hideProgressBar() {
				((ProgressBar) mainView.findViewById(R.id.snap_to_road_progress_bar)).setVisibility(View.GONE);
				progressBarVisible = false;
			}

			@Override
			public void refresh() {
				measurementLayer.refreshMap();
				updateDistancePointsText();
			}
		});

		measurementLayer.setEditingCtx(editingCtx);

		// If rotate the screen from landscape to portrait when the list of points is displayed then
		// the RecyclerViewFragment will exist without view. This is necessary to remove it.
		if (!portrait) {
			hidePointsListFragment();
		}

		nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();
		portrait = AndroidUiHelper.isOrientationPortrait(mapActivity);

		pointsSt = getString(R.string.shared_string_gpx_points).toLowerCase();

		View view = UiUtilities.getInflater(getContext(), nightMode)
				.inflate(R.layout.fragment_measurement_tool, container, false);

		mainView = view.findViewById(R.id.main_view);
		AndroidUtils.setBackground(mapActivity, mainView, nightMode, R.drawable.bg_bottom_menu_light, R.drawable.bg_bottom_menu_dark);
		additionalInfoContainer = mainView.findViewById(R.id.additional_info_container);
		customRadioButton = mainView.findViewById(R.id.custom_radio_buttons);

		View pointListBtn = customRadioButton.findViewById(R.id.left_button_container);
		TextView tvPointListBtn = customRadioButton.findViewById(R.id.left_button);
		tvPointListBtn.setText(R.string.shared_string_gpx_points);
		pointListBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				updateAdditionalInfoContainer(AdditionalInfoType.POINTS);
			}
		});

		View graphBtn = customRadioButton.findViewById(R.id.right_button_container);
		TextView tvGraphBtn = customRadioButton.findViewById(R.id.right_button);
		tvGraphBtn.setText(R.string.shared_string_graph);
		graphBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				updateAdditionalInfoContainer(AdditionalInfoType.GRAPH);
			}
		});

		if (progressBarVisible) {
			showProgressBar();
		}

		distanceTv = (TextView) mainView.findViewById(R.id.measurement_distance_text_view);
		pointsTv = (TextView) mainView.findViewById(R.id.measurement_points_text_view);
		distanceToCenterTv = (TextView) mainView.findViewById(R.id.distance_to_center_text_view);

		mainIcon = (ImageView) mainView.findViewById(R.id.main_icon);
		upDownBtn = (ImageView) mainView.findViewById(R.id.up_down_button);
		updateUpDownBtn();

		mainView.findViewById(R.id.cancel_move_point_button).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				cancelMovePointMode();
			}
		});

		mainView.findViewById(R.id.cancel_point_before_after_button).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				cancelAddPointBeforeOrAfterMode();
			}
		});

		View upDownRow = mainView.findViewById(R.id.up_down_row);
		upDownRow.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if (!additionalInfoOpened && editingCtx.getPointsCount() > 0 && editingCtx.getSelectedPointPosition() == -1) {
					expandAdditionalInfo();
				} else {
					collapseAdditionalInfo();
				}
			}
		});

		View applyMovePointButton = mainView.findViewById(R.id.apply_move_point_button);
		UiUtilities.setupDialogButton(nightMode, applyMovePointButton, UiUtilities.DialogButtonType.PRIMARY,
				R.string.shared_string_apply);
		applyMovePointButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				applyMovePointMode();
			}
		});


		View applyPointBeforeAfterButton = mainView.findViewById(R.id.apply_point_before_after_point_button);
		UiUtilities.setupDialogButton(nightMode, applyPointBeforeAfterButton, UiUtilities.DialogButtonType.PRIMARY,
				R.string.shared_string_apply);
		applyPointBeforeAfterButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				applyAddPointBeforeAfterMode();
			}
		});

		View addPointBeforeAfterButton = mainView.findViewById(R.id.add_point_before_after_button);
		UiUtilities.setupDialogButton(nightMode, addPointBeforeAfterButton, UiUtilities.DialogButtonType.PRIMARY,
				R.string.shared_string_add);
		addPointBeforeAfterButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				addPointBeforeAfter();
			}
		});

		mainView.findViewById(R.id.options_button).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				OptionsBottomSheetDialogFragment.showInstance(mapActivity.getSupportFragmentManager(),
						MeasurementToolFragment.this,
						editingCtx.isTrackSnappedToRoad() || editingCtx.isNewData(),
						editingCtx.getAppMode().getStringKey()
				);
			}
		});

		undoBtn = ((ImageButton) mainView.findViewById(R.id.undo_point_button));
		redoBtn = ((ImageButton) mainView.findViewById(R.id.redo_point_button));

		Drawable undoDrawable = getActiveIcon(R.drawable.ic_action_undo_dark);
		undoBtn.setImageDrawable(AndroidUtils.getDrawableForDirection(mapActivity, undoDrawable));
		undoBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				editingCtx.getCommandManager().undo();
				updateUndoRedoButton(editingCtx.getCommandManager().canUndo(), undoBtn);
				updateUndoRedoButton(true, redoBtn);
				updateUndoRedoCommonStuff();
			}
		});

		Drawable redoDrawable = getActiveIcon(R.drawable.ic_action_redo_dark);
		redoBtn.setImageDrawable(AndroidUtils.getDrawableForDirection(mapActivity, redoDrawable));
		redoBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				editingCtx.getCommandManager().redo();
				updateUndoRedoButton(editingCtx.getCommandManager().canRedo(), redoBtn);
				updateUndoRedoButton(true, undoBtn);
				updateUndoRedoCommonStuff();
			}
		});

		View addPointButton = mainView.findViewById(R.id.add_point_button);
		UiUtilities.setupDialogButton(nightMode, addPointButton, UiUtilities.DialogButtonType.PRIMARY,
				R.string.shared_string_add);
		addPointButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				addCenterPoint();
			}
		});

		measurementLayer.setOnSingleTapListener(new MeasurementToolLayer.OnSingleTapListener() {
			@Override
			public void onAddPoint() {
				addPoint();
			}

			@Override
			public void onSelectPoint(int selectedPointPos) {
				if (additionalInfoOpened) {
					collapseAdditionalInfo();
				}
				if (selectedPointPos != -1) {
					openSelectedPointMenu(mapActivity);
				}
			}
		});

		measurementLayer.setOnMeasureDistanceToCenterListener(new MeasurementToolLayer.OnMeasureDistanceToCenter() {
			@Override
			public void onMeasure(float distance, float bearing) {
				String distStr = OsmAndFormatter.getFormattedDistance(distance, mapActivity.getMyApplication());
				String azimuthStr = OsmAndFormatter.getFormattedAzimuth(bearing, getMyApplication());
				distanceToCenterTv.setText(String.format("%1$s • %2$s", distStr, azimuthStr));
				TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
						distanceToCenterTv, 14, 18, 2,
						TypedValue.COMPLEX_UNIT_SP);
			}
		});

		measurementLayer.setOnEnterMovePointModeListener(new MeasurementToolLayer.OnEnterMovePointModeListener() {
			@Override
			public void onEnterMovePointMode() {
				if (additionalInfoOpened) {
					collapseAdditionalInfo();
				}
				switchMovePointMode(true);
			}
		});

		if (!editingCtx.getCommandManager().canUndo()) {
			updateUndoRedoButton(false, undoBtn);
		}
		if (!editingCtx.getCommandManager().canRedo()) {
			updateUndoRedoButton(false, redoBtn);
		}
		if (editingCtx.getPointsCount() < 1) {
			disable(upDownBtn);
		}

		toolBarController = new MeasurementToolBarController();
		if (editingCtx.getSelectedPointPosition() != -1) {
			int navigationIconResId = AndroidUtils.getNavigationIconResId(mapActivity);
			toolBarController.setBackBtnIconIds(navigationIconResId, navigationIconResId);
		} else {
			toolBarController.setBackBtnIconIds(R.drawable.ic_action_remove_dark, R.drawable.ic_action_remove_dark);
		}
		toolBarController.setOnBackButtonClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				quit(false);
			}
		});
		toolBarController.setOnSaveViewClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				saveChanges(SaveAction.SHOW_SNACK_BAR_AND_CLOSE);
			}
		});
		updateToolbar();

		final GpxData gpxData = editingCtx.getGpxData();

		ImageButton snapToRoadBtn = (ImageButton) mapActivity.findViewById(R.id.snap_to_road_image_button);
		snapToRoadBtn.setBackgroundResource(nightMode ? R.drawable.btn_circle_night : R.drawable.btn_circle);
		snapToRoadBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				startSnapToRoad(false);
			}
		});
		snapToRoadBtn.setVisibility(View.VISIBLE);

		initMeasurementMode(gpxData);

		if (savedInstanceState == null) {
			if (fileName != null) {
				addNewGpxData(getGpxFile(fileName));
			} else if (editingCtx.isNewData() && planRouteMode && initialPoint == null) {
				StartPlanRouteBottomSheet.showInstance(mapActivity.getSupportFragmentManager(),
						createStartPlanRouteListener());
			} else if (!editingCtx.isNewData() && !editingCtx.hasRoutePoints() && !editingCtx.hasRoute() && editingCtx.getPointsCount() > 1) {
				enterApproximationMode(mapActivity);
			}
		}

		return view;
	}

	private void enterApproximationMode(MapActivity mapActivity) {
		MeasurementToolLayer layer = getMeasurementLayer();
		if (layer != null) {
			layer.setTapsDisabled(true);
			SnapTrackWarningBottomSheet.showInstance(mapActivity.getSupportFragmentManager(), this);
		}
	}

	private void updateAdditionalInfoContainer(@NonNull AdditionalInfoType additionalInfoType) {
		if (!additionalInfoOpened || !additionalInfoType.equals(currentAdditionalInfoType)) {
			additionalInfoOpened = true;
			updateUpDownBtn();
			currentAdditionalInfoType = additionalInfoType;
			OsmandApplication app = getMyApplication();
			if (AdditionalInfoType.POINTS.equals(additionalInfoType)) {
				UiUtilities.updateCustomRadioButtons(app, customRadioButton, nightMode, LEFT);
			} else if (AdditionalInfoType.GRAPH.equals(additionalInfoType)) {
				UiUtilities.updateCustomRadioButtons(app, customRadioButton, nightMode, RIGHT);
			} else {
				return;
			}
			setAdditionalInfoFragment(additionalInfoType.fragmentName);
		}
	}

	private void updateAdditionalInfoView() {
		Fragment fragment = getActiveAdditionalInfoFragment();
		if (fragment instanceof OnUpdateAdditionalInfoListener) {
			((OnUpdateAdditionalInfoListener) fragment).onUpdateAdditionalInfo();
		}
	}

	public boolean isInEditMode() {
		return !planRouteMode && !editingCtx.isNewData() && !directionMode;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public MeasurementEditingContext getEditingContext() {
		return editingCtx;
	}

	private void updateUndoRedoCommonStuff() {
		hidePointsListIfNoPoints();
		if (editingCtx.getPointsCount() > 0) {
			enable(upDownBtn);
		}
		updateAdditionalInfoView();
		updateDistancePointsText();
		updateSnapToRoadControls();
	}

	private void initMeasurementMode(GpxData gpxData) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			editingCtx.getCommandManager().setMeasurementLayer(mapActivity.getMapLayers().getMeasurementToolLayer());
			enterMeasurementMode();
			updateSnapToRoadControls();
			if (gpxData != null) {
				List<WptPt> points = gpxData.getGpxFile().getRoutePoints();
				if (!points.isEmpty()) {
					ApplicationMode snapToRoadAppMode = ApplicationMode.valueOfStringKey(points.get(points.size() - 1).getProfileType(), null);
					if (snapToRoadAppMode != null) {
						setAppMode(snapToRoadAppMode);
					}
				}
				ActionType actionType = gpxData.getActionType();
				if (actionType == ActionType.ADD_ROUTE_POINTS) {
					displayRoutePoints();
				} else if (actionType == ActionType.EDIT_SEGMENT) {
					displaySegmentPoints();
				}
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getMapLayers().getMapControlsLayer().showMapControlsIfHidden();
			cachedMapPosition = mapActivity.getMapView().getMapPosition();
			setDefaultMapPosition();
			addInitialPoint();
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		setMapPosition(cachedMapPosition);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		cancelModes();
		exitMeasurementMode();
		if (additionalInfoOpened) {
			collapseAdditionalInfo();
		}
		MeasurementToolLayer layer = getMeasurementLayer();
		if (layer != null) {
			layer.setOnSingleTapListener(null);
			layer.setOnEnterMovePointModeListener(null);
		}
	}

	@Override
	public int getStatusBarColorId() {
		return R.color.status_bar_transparent_gradient;
	}

	@Nullable
	private MapActivity getMapActivity() {
		Activity activity = getActivity();
		if (AndroidUtils.isActivityNotDestroyed(activity)) {
			return (MapActivity) activity;
		}
		return null;
	}

	public MeasurementToolLayer getMeasurementLayer() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			return mapActivity.getMapLayers().getMeasurementToolLayer();
		}
		return null;
	}

	@Override
	protected Drawable getContentIcon(@DrawableRes int id) {
		return getIcon(id, nightMode ? R.color.icon_color_default_dark : R.color.icon_color_default_light);
	}

	private Drawable getActiveIcon(@DrawableRes int id) {
		return getIcon(id, nightMode ? R.color.osmand_orange : R.color.color_myloc_distance);
	}

	private void showProgressBar() {
		ProgressBar progressBar = (ProgressBar) mainView.findViewById(R.id.snap_to_road_progress_bar);
		progressBar.setVisibility(View.VISIBLE);
		progressBar.setMinimumHeight(0);
		progressBar.setProgress(0);
		progressBarVisible = true;
	}

	private void updateMainIcon() {
		GpxData gpxData = editingCtx.getGpxData();
		if (gpxData != null) {
			ActionType actionType = gpxData.getActionType();
			if (actionType == ActionType.ADD_SEGMENT || actionType == ActionType.EDIT_SEGMENT) {
				mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_polygom_dark));
			} else {
				mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_markers_dark));
			}
		} else {
			mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_ruler));
		}
	}

	private void startSnapToRoad(boolean rememberPreviousTitle) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (rememberPreviousTitle) {
				previousToolBarTitle = toolBarController.getTitle();
			}
			toolBarController.setTitle(getString(R.string.route_between_points));
			mapActivity.refreshMap();

			if (editingCtx.isNewData() || editingCtx.hasRoutePoints() || editingCtx.hasRoute() || editingCtx.getPointsCount() < 2) {
				RouteBetweenPointsBottomSheetDialogFragment.showInstance(mapActivity.getSupportFragmentManager(),
						this, RouteBetweenPointsDialogType.WHOLE_ROUTE_CALCULATION,
						editingCtx.getLastCalculationMode() == CalculationMode.NEXT_SEGMENT
								? RouteBetweenPointsDialogMode.SINGLE
								: RouteBetweenPointsDialogMode.ALL,
						editingCtx.getAppMode());
			} else {
				enterApproximationMode(mapActivity);
			}
		}
	}

	public void saveChanges(SaveAction saveAction) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (editingCtx.getPointsCount() > 0) {
				GpxData gpxData = editingCtx.getGpxData();
				if (editingCtx.isNewData()) {
					saveAsGpx(SaveType.ROUTE_POINT, saveAction);
				} else if (isInEditMode() && gpxData.getActionType() == ActionType.EDIT_SEGMENT) {
					openSaveAsNewTrackMenu(mapActivity);
				} else {
					addToGpx(mapActivity, saveAction);
				}
			} else {
				Toast.makeText(mapActivity, getString(R.string.none_point_error), Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case SnapTrackWarningBottomSheet.REQUEST_CODE:
				switch (resultCode) {
					case SnapTrackWarningBottomSheet.CANCEL_RESULT_CODE:
						toolBarController.setSaveViewVisible(true);
						directionMode = false;
						exitApproximationMode();
						updateToolbar();
						break;
					case SnapTrackWarningBottomSheet.CONTINUE_RESULT_CODE:
						MapActivity mapActivity = getMapActivity();
						if (mapActivity != null) {
							GpxApproximationFragment.showInstance(mapActivity.getSupportFragmentManager(),
									this, new LocationsHolder(editingCtx.getPoints()));
						}
						break;
				}
				break;
			case ExitBottomSheetDialogFragment.REQUEST_CODE:
				switch (resultCode) {
					case ExitBottomSheetDialogFragment.EXIT_RESULT_CODE:
						dismiss(getMapActivity());
						break;
					case ExitBottomSheetDialogFragment.SAVE_RESULT_CODE:
						openSaveAsNewTrackMenu(getMapActivity());
						break;
				}
		}
	}

	@Override
	public void snapToRoadOnCLick() {
		startSnapToRoad(true);
	}

	@Override
	public void directionsOnClick() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			OsmandApplication app = mapActivity.getMyApplication();
			MapActivityActions mapActions = mapActivity.getMapActions();
			TargetPointsHelper targetPointsHelper = app.getTargetPointsHelper();
			ApplicationMode appMode = editingCtx.getAppMode();
			if (appMode == ApplicationMode.DEFAULT) {
				appMode = null;
			}
			List<WptPt> points = editingCtx.getPoints();
			if (points.size() > 0) {
				if (points.size() == 1) {
					targetPointsHelper.clearAllPoints(false);
					targetPointsHelper.navigateToPoint(new LatLon(points.get(0).getLatitude(), points.get(0).getLongitude()), false, -1);
					dismiss(mapActivity);
					mapActions.enterRoutePlanningModeGivenGpx(null, appMode, null, null, true, true, MenuState.HEADER_ONLY);
				} else {
					String trackName = getSuggestedFileName();
					if (editingCtx.hasRoute()) {
						GPXFile gpx = editingCtx.exportRouteAsGpx(trackName);
						if (gpx != null) {
							dismiss(mapActivity);
							runNavigation(gpx, appMode);
						} else {
							Toast.makeText(mapActivity, getString(R.string.error_occurred_saving_gpx), Toast.LENGTH_SHORT).show();
						}
					} else {
						if (editingCtx.isNewData() || editingCtx.hasRoutePoints()) {
							GPXFile gpx = new GPXFile(Version.getFullVersion(requireMyApplication()));
							gpx.addRoutePoints(points);
							dismiss(mapActivity);
							targetPointsHelper.clearAllPoints(false);
							mapActions.enterRoutePlanningModeGivenGpx(gpx, appMode, null, null, true, true, MenuState.HEADER_ONLY);
						} else {
							directionMode = true;
							enterApproximationMode(mapActivity);
						}
					}
				}
			} else {
				Toast.makeText(mapActivity, getString(R.string.none_point_error), Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void runNavigation(final GPXFile gpx, final ApplicationMode appMode) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (mapActivity.getMyApplication().getRoutingHelper().isFollowingMode()) {
				mapActivity.getMapActions().stopNavigationActionConfirm(new Runnable() {
					@Override
					public void run() {
						MapActivity mapActivity = getMapActivity();
						if (mapActivity != null) {
							mapActivity.getMapActions().enterRoutePlanningModeGivenGpx(gpx, appMode, null, null, true, true, MenuState.HEADER_ONLY);
						}
					}
				});
			} else {
				mapActivity.getMapActions().stopNavigationWithoutConfirm();
				mapActivity.getMapActions().enterRoutePlanningModeGivenGpx(gpx, appMode, null, null, true, true, MenuState.HEADER_ONLY);
			}
		}
	}

	@Override
	public void saveChangesOnClick() {
		saveChanges(SaveAction.SHOW_TOAST);
	}

	@Override
	public void saveAsNewTrackOnClick() {
		openSaveAsNewTrackMenu(getMapActivity());
	}

	@Override
	public void addToTheTrackOnClick() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (editingCtx.getPointsCount() > 0) {
				showAddToTrackDialog(mapActivity);
			} else {
				Toast.makeText(mapActivity, getString(R.string.none_point_error), Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void clearAllOnClick() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		editingCtx.getCommandManager().execute(new ClearPointsCommand(measurementLayer, ALL));
		editingCtx.cancelSnapToRoad();
		if (additionalInfoOpened) {
			collapseAdditionalInfo();
		}
		updateUndoRedoButton(false, redoBtn);
		disable(upDownBtn);
		updateDistancePointsText();
	}

	@Override
	public void reverseRouteOnClick() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			List<WptPt> points = editingCtx.getPoints();
			if (points.size() > 1) {
				MeasurementToolLayer measurementLayer = getMeasurementLayer();
				editingCtx.getCommandManager().execute(new ReversePointsCommand(measurementLayer));
				if (additionalInfoOpened) {
					collapseAdditionalInfo();
				}
				updateUndoRedoButton(false, redoBtn);
				updateUndoRedoButton(true, undoBtn);
				updateDistancePointsText();
			} else {
				Toast.makeText(mapActivity, getString(R.string.one_point_error), Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void onMovePoint() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			measurementLayer.enterMovingPointMode();
		}
		switchMovePointMode(true);
	}

	@Override
	public void onDeletePoint() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			removePoint(measurementLayer, editingCtx.getSelectedPointPosition());
		}
		editingCtx.setSelectedPointPosition(-1);
	}

	@Override
	public void onAddPointAfter() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			measurementLayer.moveMapToPoint(editingCtx.getSelectedPointPosition());
			editingCtx.setInAddPointMode(true);
			editingCtx.splitSegments(editingCtx.getSelectedPointPosition() + 1);
		}
		((TextView) mainView.findViewById(R.id.add_point_before_after_text)).setText(mainView.getResources().getString(R.string.add_point_after));
		mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_addpoint_above));
		switchAddPointBeforeAfterMode(true);
	}

	@Override
	public void onAddPointBefore() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			measurementLayer.moveMapToPoint(editingCtx.getSelectedPointPosition());
			editingCtx.setInAddPointMode(true);
			editingCtx.splitSegments(editingCtx.getSelectedPointPosition());
		}
		((TextView) mainView.findViewById(R.id.add_point_before_after_text)).setText(mainView.getResources().getString(R.string.add_point_before));
		mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_addpoint_below));
		switchAddPointBeforeAfterMode(true);
	}

	@Override
	public void onTrimRouteBefore() {
		trimRoute(BEFORE);
	}

	@Override
	public void onTrimRouteAfter() {
		trimRoute(AFTER);
	}

	private void trimRoute(ClearCommandMode before) {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		editingCtx.getCommandManager().execute(new ClearPointsCommand(measurementLayer, before));
		if (additionalInfoOpened) {
			collapseAdditionalInfo();
		}
		editingCtx.setSelectedPointPosition(-1);
		editingCtx.splitSegments(editingCtx.getBeforePoints().size() + editingCtx.getAfterPoints().size());
		updateUndoRedoButton(false, redoBtn);
		updateUndoRedoButton(true, undoBtn);
		updateDistancePointsText();
	}

	@Override
	public void onChangeRouteTypeBefore() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			RouteBetweenPointsBottomSheetDialogFragment.showInstance(mapActivity.getSupportFragmentManager(),
					this, RouteBetweenPointsDialogType.PREV_ROUTE_CALCULATION,
					RouteBetweenPointsDialogMode.SINGLE,
					editingCtx.getBeforeSelectedPointAppMode());
		}
	}

	@Override
	public void onChangeRouteTypeAfter() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			RouteBetweenPointsBottomSheetDialogFragment.showInstance(mapActivity.getSupportFragmentManager(),
					this, RouteBetweenPointsDialogType.NEXT_ROUTE_CALCULATION,
					RouteBetweenPointsDialogMode.SINGLE,
					editingCtx.getSelectedPointAppMode());
		}
	}

	@Override
	public void onCloseMenu() {
		setDefaultMapPosition();
	}

	@Override
	public void onClearSelection() {
		editingCtx.setSelectedPointPosition(-1);
	}

	@Override
	public void onCloseRouteDialog() {
		toolBarController.setTitle(previousToolBarTitle);
		editingCtx.setSelectedPointPosition(-1);
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.refreshMap();
		}
	}

	@Override
	public void onChangeApplicationMode(ApplicationMode mode, RouteBetweenPointsDialogType dialogType,
										RouteBetweenPointsDialogMode dialogMode) {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			ChangeRouteType changeRouteType = ChangeRouteType.NEXT_SEGMENT;
			switch (dialogType) {
				case WHOLE_ROUTE_CALCULATION:
					changeRouteType = dialogMode == RouteBetweenPointsDialogMode.SINGLE
							? ChangeRouteType.LAST_SEGMENT : ChangeRouteType.WHOLE_ROUTE;
					break;
				case NEXT_ROUTE_CALCULATION:
					changeRouteType = dialogMode == RouteBetweenPointsDialogMode.SINGLE
							? ChangeRouteType.NEXT_SEGMENT : ChangeRouteType.ALL_NEXT_SEGMENTS;
					break;
				case PREV_ROUTE_CALCULATION:
					changeRouteType = dialogMode == RouteBetweenPointsDialogMode.SINGLE
							? ChangeRouteType.PREV_SEGMENT : ChangeRouteType.ALL_PREV_SEGMENTS;
					break;
			}
			editingCtx.getCommandManager().execute(new ChangeRouteModeCommand(measurementLayer, mode, changeRouteType, editingCtx.getSelectedPointPosition()));
			updateUndoRedoButton(false, redoBtn);
			updateUndoRedoButton(true, undoBtn);
			disable(upDownBtn);
			updateSnapToRoadControls();
			updateDistancePointsText();
		}
	}

	private StartPlanRouteListener createStartPlanRouteListener() {
		return new StartPlanRouteListener() {
			@Override
			public void openExistingTrackOnClick() {
				MapActivity mapActivity = getMapActivity();
				if (mapActivity != null) {
					SelectFileBottomSheet.showInstance(mapActivity.getSupportFragmentManager(),
							createSelectFileListener(), OPEN_TRACK);
				}
			}

			@Override
			public void openLastEditTrackOnClick(String gpxFileName) {
				addNewGpxData(getGpxFile(gpxFileName));
			}

			@Override
			public void dismissButtonOnClick() {
				quit(true);
			}
		};
	}

	private SelectFileListener createSelectFileListener() {
		return new SelectFileListener() {
			@Override
			public void selectFileOnCLick(String gpxFileName) {
				addNewGpxData(getGpxFile(gpxFileName));
			}

			@Override
			public void dismissButtonOnClick() {
				MapActivity mapActivity = getMapActivity();
				if (mapActivity != null) {
					StartPlanRouteBottomSheet.showInstance(mapActivity.getSupportFragmentManager(),
							createStartPlanRouteListener());
				}
			}
		};
	}

	private GPXFile getGpxFile(String gpxFileName) {
		OsmandApplication app = getMyApplication();
		GPXFile gpxFile = null;
		if (app != null) {
			SelectedGpxFile selectedGpxFile = app.getSelectedGpxHelper().getSelectedFileByName(gpxFileName);
			if (selectedGpxFile != null) {
				gpxFile = selectedGpxFile.getGpxFile();
			} else {
				gpxFile = GPXUtilities.loadGPXFile(new File(
						getMyApplication().getAppPath(IndexConstants.GPX_INDEX_DIR), gpxFileName));
			}

		}
		return gpxFile;
	}

	private SelectFileListener createAddToTrackFileListener() {
		final MapActivity mapActivity = getMapActivity();
		return new SelectFileListener() {
			@Override
			public void selectFileOnCLick(String gpxFileName) {
				if (mapActivity != null) {
					GPXFile gpxFile;
					if (gpxFileName == null) {
						gpxFile = mapActivity.getMyApplication().getSavingTrackHelper().getCurrentGpx();
					} else {
						gpxFile = getGpxFile(gpxFileName);
					}
					SelectedGpxFile selectedGpxFile = mapActivity.getMyApplication().getSelectedGpxHelper()
							.getSelectedFileByPath(gpxFile.path);
					boolean showOnMap = selectedGpxFile != null;
					saveExistingGpx(gpxFile, showOnMap, ActionType.ADD_SEGMENT,
							editingCtx.hasRoute() ? SaveType.ROUTE_POINT : SaveType.LINE, SaveAction.SHOW_TOAST);
				}
			}

			@Override
			public void dismissButtonOnClick() {
			}
		};
	}

	public void addNewGpxData(GPXFile gpxFile) {
		QuadRect rect = gpxFile.getRect();
		TrkSegment segment = gpxFile.getNonEmptyTrkSegment();
		ActionType actionType = segment == null ? ActionType.ADD_ROUTE_POINTS : ActionType.EDIT_SEGMENT;
		GpxData gpxData = new GpxData(gpxFile, rect, actionType, segment);
		editingCtx.setGpxData(gpxData);
		initMeasurementMode(gpxData);
		QuadRect qr = gpxData.getRect();
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getMapView().fitRectToMap(qr.left, qr.right, qr.top, qr.bottom,
					(int) qr.width(), (int) qr.height(), 0);
		}
	}

	public void removePoint(MeasurementToolLayer measurementLayer, int position) {
		if (measurementLayer != null) {
			editingCtx.getCommandManager().execute(new RemovePointCommand(measurementLayer, position));
			updateAdditionalInfoView();
			updateUndoRedoButton(true, undoBtn);
			updateUndoRedoButton(false, redoBtn);
			updateDistancePointsText();
			hidePointsListIfNoPoints();
		}
	}

	@Override
	public void onSaveAsNewTrack(String folderName, String fileName, boolean showOnMap, boolean simplifiedTrack) {
		File dir = getMyApplication().getAppPath(IndexConstants.GPX_INDEX_DIR);
		if (folderName != null) {
			dir = new File(dir, folderName);
		}
		fileName = fileName + GPX_FILE_EXT;
		SaveType saveType = simplifiedTrack ? SaveType.LINE : SaveType.ROUTE_POINT;
		saveNewGpx(dir, fileName, showOnMap, saveType, SaveAction.SHOW_IS_SAVED_FRAGMENT);
	}

	private void setAppMode(@NonNull ApplicationMode appMode) {
		editingCtx.setAppMode(appMode);
		editingCtx.scheduleRouteCalculateIfNotEmpty();
		updateSnapToRoadControls();
	}

	private void resetAppMode() {
		toolBarController.setTopBarSwitchVisible(false);
		toolBarController.setTitle(previousToolBarTitle);
		mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_ruler));
		editingCtx.resetAppMode();
		editingCtx.cancelSnapToRoad();
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mainView.findViewById(R.id.snap_to_road_progress_bar).setVisibility(View.GONE);
			mapActivity.refreshMap();
		}
	}

	private void updateSnapToRoadControls() {
		final MapActivity mapActivity = getMapActivity();
		final ApplicationMode appMode = editingCtx.getAppMode();
		if (mapActivity != null) {
			Drawable icon;
			if (appMode == MeasurementEditingContext.DEFAULT_APP_MODE) {
				icon = getActiveIcon(R.drawable.ic_action_split_interval);
			} else {
				icon = getIcon(appMode.getIconRes(), appMode.getIconColorInfo().getColor(nightMode));
			}
			ImageButton snapToRoadBtn = (ImageButton) mapActivity.findViewById(R.id.snap_to_road_image_button);
			snapToRoadBtn.setImageDrawable(icon);
			mapActivity.refreshMap();
		}
	}

	private void hideSnapToRoadIcon() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.findViewById(R.id.snap_to_road_image_button).setVisibility(View.GONE);
		}
	}

	private void displayRoutePoints() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		GpxData gpxData = editingCtx.getGpxData();
		GPXFile gpx = gpxData != null ? gpxData.getGpxFile() : null;
		if (gpx != null) {
			List<WptPt> points = gpx.getRoutePoints();
			if (measurementLayer != null) {
				editingCtx.addPoints(points);
				updateAdditionalInfoView();
				updateDistancePointsText();
			}
		}
	}

	private void displaySegmentPoints() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			editingCtx.addPoints();
			updateAdditionalInfoView();
			updateDistancePointsText();
		}
	}

	private void openSelectedPointMenu(MapActivity mapActivity) {
		if (mapActivity != null) {
			SelectedPointBottomSheetDialogFragment.showInstance(mapActivity.getSupportFragmentManager(), this);
		}
	}

	private void openSaveAsNewTrackMenu(MapActivity mapActivity) {
		if (mapActivity != null) {
			if (editingCtx.getPointsCount() > 0) {
				SaveAsNewTrackBottomSheetDialogFragment.showInstance(mapActivity.getSupportFragmentManager(),
						this, getSuggestedFileName());
			} else {
				Toast.makeText(mapActivity, getString(R.string.none_point_error), Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void showAddToTrackDialog(final MapActivity mapActivity) {
		if (mapActivity != null) {
			SelectFileBottomSheet.showInstance(mapActivity.getSupportFragmentManager(),
					createAddToTrackFileListener(), ADD_TO_TRACK);
		}
	}

	private void applyMovePointMode() {
		switchMovePointMode(false);
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			WptPt oldPoint = editingCtx.getOriginalPointToMove();
			WptPt newPoint = measurementLayer.getMovedPointToApply();
			int position = editingCtx.getSelectedPointPosition();
			editingCtx.getCommandManager().execute(new MovePointCommand(measurementLayer, oldPoint, newPoint, position));
			editingCtx.addPoint(newPoint);
			exitMovePointMode(false);
			doAddOrMovePointCommonStuff();
			measurementLayer.refreshMap();
		}
	}

	private void cancelMovePointMode() {
		switchMovePointMode(false);
		exitMovePointMode(true);
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.refreshMap();
		}
	}

	void exitMovePointMode(boolean cancelled) {
		if (cancelled) {
			WptPt pt = editingCtx.getOriginalPointToMove();
			editingCtx.addPoint(pt);
		}
		editingCtx.setOriginalPointToMove(null);
		editingCtx.setSelectedPointPosition(-1);
		editingCtx.splitSegments(editingCtx.getBeforePoints().size() + editingCtx.getAfterPoints().size());
	}

	public void cancelModes() {
		if (editingCtx.getOriginalPointToMove() != null) {
			cancelMovePointMode();
		} else if (editingCtx.isInAddPointMode()) {
			cancelAddPointBeforeOrAfterMode();
		}
	}

	private void addPointBeforeAfter() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			int selectedPoint = editingCtx.getSelectedPointPosition();
			int pointsCount = editingCtx.getPointsCount();
			if (addCenterPoint()) {
				if (selectedPoint == pointsCount) {
					editingCtx.splitSegments(editingCtx.getPointsCount() - 1);
				} else {
					editingCtx.setSelectedPointPosition(selectedPoint + 1);
				}
				measurementLayer.refreshMap();
			}
		}
	}

	private void applyAddPointBeforeAfterMode() {
		switchAddPointBeforeAfterMode(false);
		editingCtx.splitSegments(editingCtx.getBeforePoints().size() + editingCtx.getAfterPoints().size());
		editingCtx.setSelectedPointPosition(-1);
		editingCtx.setInAddPointMode(false);
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			measurementLayer.refreshMap();
		}
		updateDistancePointsText();
	}

	private void cancelAddPointBeforeOrAfterMode() {
		switchAddPointBeforeAfterMode(false);
		editingCtx.splitSegments(editingCtx.getBeforePoints().size() + editingCtx.getAfterPoints().size());
		editingCtx.setSelectedPointPosition(-1);
		editingCtx.setInAddPointMode(false);
		MeasurementToolLayer measurementToolLayer = getMeasurementLayer();
		if (measurementToolLayer != null) {
			measurementToolLayer.refreshMap();
		}
	}

	private void switchMovePointMode(boolean enable) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (enable) {
				int navigationIconResId = AndroidUtils.getNavigationIconResId(mapActivity);
				toolBarController.setBackBtnIconIds(navigationIconResId, navigationIconResId);
			} else {
				toolBarController.setBackBtnIconIds(R.drawable.ic_action_remove_dark, R.drawable.ic_action_remove_dark);
			}
			mapActivity.showTopToolbar(toolBarController);
			markGeneralComponents(enable ? View.GONE : View.VISIBLE);
			AndroidUiHelper.setVisibility(mapActivity, enable ? View.VISIBLE : View.GONE,
					R.id.move_point_text,
					R.id.move_point_controls);
			mainIcon.setImageDrawable(getActiveIcon(enable
					? R.drawable.ic_action_move_point
					: R.drawable.ic_action_ruler));
		}
	}

	private void switchAddPointBeforeAfterMode(boolean enable) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (enable) {
				int navigationIconResId = AndroidUtils.getNavigationIconResId(mapActivity);
				toolBarController.setBackBtnIconIds(navigationIconResId, navigationIconResId);
			} else {
				toolBarController.setBackBtnIconIds(R.drawable.ic_action_remove_dark, R.drawable.ic_action_remove_dark);
			}
			mapActivity.showTopToolbar(toolBarController);
			markGeneralComponents(enable ? View.GONE : View.VISIBLE);
			AndroidUiHelper.setVisibility(mapActivity, enable ? View.VISIBLE : View.GONE,
					R.id.add_point_before_after_text,
					R.id.add_point_before_after_controls);
			if (!enable) {
				mainIcon.setImageDrawable(getActiveIcon(R.drawable.ic_action_ruler));
			}
		}
	}

	private void markGeneralComponents(int status) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			AndroidUiHelper.setVisibility(mapActivity, status,
					R.id.measurement_distance_text_view,
					R.id.measurement_points_text_view,
					R.id.distance_to_center_text_view,
					R.id.up_down_button,
					R.id.measure_mode_controls);
		}
	}

	private void addInitialPoint() {
		if (initialPoint != null) {
			MeasurementToolLayer layer = getMeasurementLayer();
			if (layer != null) {
				editingCtx.getCommandManager().execute(new AddPointCommand(layer, initialPoint));
				doAddOrMovePointCommonStuff();
				initialPoint = null;
			}
		}
	}

	private void addPoint() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			editingCtx.getCommandManager().execute(new AddPointCommand(measurementLayer, false));
			doAddOrMovePointCommonStuff();
		}
	}

	private boolean addCenterPoint() {
		boolean added = false;
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			added = editingCtx.getCommandManager().execute(new AddPointCommand(measurementLayer, true));
			doAddOrMovePointCommonStuff();
		}
		return added;
	}

	private void doAddOrMovePointCommonStuff() {
		enable(upDownBtn);
		updateUndoRedoButton(true, undoBtn);
		updateUndoRedoButton(false, redoBtn);
		updateDistancePointsText();
		updateAdditionalInfoView();
	}

	private void expandAdditionalInfo() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			additionalInfoContainer.setVisibility(View.VISIBLE);
			AdditionalInfoType typeToShow = currentAdditionalInfoType == null
					? AdditionalInfoType.POINTS : currentAdditionalInfoType;
			updateAdditionalInfoContainer(typeToShow);
			setMapPosition(portrait
					? OsmandSettings.MIDDLE_TOP_CONSTANT
					: OsmandSettings.LANDSCAPE_MIDDLE_RIGHT_CONSTANT);
		}
	}

	public void collapseAdditionalInfo() {
		additionalInfoOpened = false;
		updateUpDownBtn();
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			FragmentManager manager = getChildFragmentManager();
			Fragment activeFragment = getActiveAdditionalInfoFragment();
			if (activeFragment != null) {
				manager.beginTransaction().remove(activeFragment).commitAllowingStateLoss();
			}
			additionalInfoContainer.setVisibility(View.GONE);
			setDefaultMapPosition();
		}
	}

	private void setAdditionalInfoFragment(String fragmentName) {
		Context ctx = getContext();
		if (ctx != null) {
			Fragment fragment = Fragment.instantiate(ctx, fragmentName);
			FragmentManager fm = getChildFragmentManager();
			FragmentTransaction fragmentTransaction = fm.beginTransaction();
			fragmentTransaction.replace(R.id.fragmentContainer, fragment, fragmentName);
			fragmentTransaction.commit();
			fm.executePendingTransactions();
		}
	}

	private void hidePointsListIfNoPoints() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			if (editingCtx.getPointsCount() < 1) {
				disable(upDownBtn);
				if (additionalInfoOpened) {
					collapseAdditionalInfo();
				}
			}
		}
	}

	private void hidePointsListFragment() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			try {
				FragmentManager manager = mapActivity.getSupportFragmentManager();
				Fragment fragment = manager.findFragmentByTag(RecyclerViewFragment.TAG);
				if (fragment != null) {
					manager.beginTransaction().remove(fragment).commitAllowingStateLoss();
				}
			} catch (Exception e) {
				// ignore
			}
		}
	}

	private Fragment getActiveAdditionalInfoFragment() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			for (AdditionalInfoType type : AdditionalInfoType.values()) {
				try {
					FragmentManager fm = getChildFragmentManager();
					Fragment fragment = fm.findFragmentByTag(type.fragmentName);
					if (fragment != null) {
						return fragment;
					}
				} catch (Exception e) {
					// ignore
				}
			}
		}
		return null;
	}

	private void setDefaultMapPosition() {
		setMapPosition(OsmandSettings.CENTER_CONSTANT);
	}

	public void setMapPosition(int position) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getMapView().setMapPosition(position);
			mapActivity.refreshMap();
		}
	}

	private void addToGpx(MapActivity mapActivity, SaveAction saveAction) {
		GpxData gpxData = editingCtx.getGpxData();
		GPXFile gpx = gpxData != null ? gpxData.getGpxFile() : null;
		if (gpx != null) {
			SelectedGpxFile selectedGpxFile =
					mapActivity.getMyApplication().getSelectedGpxHelper().getSelectedFileByPath(gpx.path);
			boolean showOnMap = selectedGpxFile != null;
			saveExistingGpx(gpx, showOnMap, gpxData.getActionType(),
					editingCtx.hasRoute() ? SaveType.ROUTE_POINT : SaveType.LINE, saveAction);
		}
	}

	private void saveAsGpx(final SaveType saveType, final SaveAction saveAction) {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			final File dir = mapActivity.getMyApplication().getAppPath(IndexConstants.GPX_INDEX_DIR);
			final View view = UiUtilities.getInflater(mapActivity, nightMode).inflate(R.layout.save_gpx_dialog, null);
			final EditText nameEt = (EditText) view.findViewById(R.id.gpx_name_et);
			final TextView warningTextView = (TextView) view.findViewById(R.id.file_exists_text_view);
			final View buttonView = view.findViewById(R.id.button_view);
			final SwitchCompat showOnMapToggle = (SwitchCompat) view.findViewById(R.id.toggle_show_on_map);
			UiUtilities.setupCompoundButton(showOnMapToggle, nightMode, UiUtilities.CompoundButtonType.GLOBAL);
			buttonView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					showOnMapToggle.setChecked(!showOnMapToggle.isChecked());
				}
			});
			showOnMapToggle.setChecked(true);

			String displayedName = getSuggestedFileName();
			nameEt.setText(displayedName);
			nameEt.setSelection(displayedName.length());
			final boolean[] textChanged = new boolean[1];

			AlertDialog.Builder builder = new AlertDialog.Builder(UiUtilities.getThemedContext(mapActivity, nightMode))
					.setTitle(R.string.enter_gpx_name)
					.setView(view)
					.setPositiveButton(R.string.shared_string_save, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							final String name = nameEt.getText().toString();
							String fileName = name + GPX_FILE_EXT;
							if (textChanged[0]) {
								File fout = new File(dir, fileName);
								int ind = 1;
								while (fout.exists()) {
									fileName = name + "_" + (++ind) + GPX_FILE_EXT;
									fout = new File(dir, fileName);
								}
							}
							saveNewGpx(dir, fileName, showOnMapToggle.isChecked(), saveType, saveAction);
						}
					})
					.setNegativeButton(R.string.shared_string_cancel, null);
			final AlertDialog dialog = builder.create();
			dialog.show();

			nameEt.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

				}

				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

				}

				@Override
				public void afterTextChanged(Editable editable) {
					if (new File(dir, editable.toString() + GPX_FILE_EXT).exists()) {
						warningTextView.setVisibility(View.VISIBLE);
						warningTextView.setText(R.string.file_with_name_already_exists);
						dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
					} else if (editable.toString().trim().isEmpty()) {
						warningTextView.setVisibility(View.VISIBLE);
						warningTextView.setText(R.string.enter_the_file_name);
						dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
					} else {
						warningTextView.setVisibility(View.INVISIBLE);
						dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
					}
					textChanged[0] = true;
				}
			});
		}
	}

	private void updateUpDownBtn() {
		Drawable icon = getContentIcon(additionalInfoOpened
				? R.drawable.ic_action_arrow_down : R.drawable.ic_action_arrow_up);
		upDownBtn.setImageDrawable(icon);
	}

	private String getSuggestedFileName() {
		GpxData gpxData = editingCtx.getGpxData();
		String displayedName;
		if (gpxData == null) {
			final String suggestedName = new SimpleDateFormat("EEE dd MMM yyyy", Locale.US).format(new Date());
			displayedName = suggestedName;
			OsmandApplication app = getMyApplication();
			if (app != null) {
				File dir = app.getAppPath(IndexConstants.GPX_INDEX_DIR);
				File fout = new File(dir, suggestedName + GPX_FILE_EXT);
				int ind = 0;
				while (fout.exists()) {
					displayedName = suggestedName + "_" + (++ind);
					fout = new File(dir, displayedName + GPX_FILE_EXT);
				}
			}
		} else {
			displayedName = AndroidUtils.trimExtension(new File(gpxData.getGpxFile().path).getName());
		}
		return displayedName;
	}

	private void saveNewGpx(File dir, String fileName, boolean showOnMap, SaveType saveType, SaveAction saveAction) {
		saveGpx(dir, fileName, showOnMap, null, null, saveType, saveAction);
	}

	private void saveExistingGpx(GPXFile gpx, boolean showOnMap, ActionType actionType, SaveType saveType,
	                             SaveAction saveAction) {
		saveGpx(null, null, showOnMap, gpx, actionType, saveType, saveAction);
	}

	@SuppressLint("StaticFieldLeak")
	private void saveGpx(final File dir,
	                     final String fileName,
	                     final boolean showOnMap,
	                     final GPXFile gpxFile,
	                     final ActionType actionType,
	                     final SaveType saveType,
	                     final SaveAction saveAction) {

		new AsyncTask<Void, Void, Exception>() {

			private ProgressDialog progressDialog;
			private File toSave;
			private GPXFile savedGpxFile;

			@Override
			protected void onPreExecute() {
				cancelModes();
				MapActivity activity = getMapActivity();
				if (activity != null) {
					progressDialog = new ProgressDialog(activity);
					progressDialog.setMessage(getString(R.string.saving_gpx_tracks));
					progressDialog.show();
				}
			}

			@Override
			protected Exception doInBackground(Void... voids) {
				MeasurementToolLayer measurementLayer = getMeasurementLayer();
				OsmandApplication app = getMyApplication();
				if (app == null) {
					return null;
				}
				List<WptPt> points = editingCtx.getPoints();
				TrkSegment before = editingCtx.getBeforeTrkSegmentLine();
				TrkSegment after = editingCtx.getAfterTrkSegmentLine();
				if (gpxFile == null) {
					toSave = new File(dir, fileName);
					String trackName = fileName.substring(0, fileName.length() - GPX_FILE_EXT.length());
					GPXFile gpx = new GPXFile(Version.getFullVersion(app));
					if (measurementLayer != null) {
						if (saveType == SaveType.LINE) {
							TrkSegment segment = new TrkSegment();
							if (editingCtx.hasRoute()) {
								segment.points.addAll(editingCtx.getDistinctRoutePoints());
							} else {
								segment.points.addAll(before.points);
								segment.points.addAll(after.points);
							}
							Track track = new Track();
							track.name = trackName;
							track.segments.add(segment);
							gpx.tracks.add(track);
						} else if (saveType == SaveType.ROUTE_POINT) {
							if (editingCtx.hasRoute()) {
								GPXFile newGpx = editingCtx.exportRouteAsGpx(trackName);
								if (newGpx != null) {
									gpx = newGpx;
								}
							}
							gpx.addRoutePoints(points);
						}
					}
					Exception res = GPXUtilities.writeGpxFile(toSave, gpx);
					gpx.path = toSave.getAbsolutePath();
					savedGpxFile = gpx;
					if (showOnMap) {
						app.getSelectedGpxHelper().selectGpxFile(gpx, true, false);
					}
					return res;
				} else {
					GPXFile gpx = gpxFile;
					toSave = new File(gpx.path);
					String trackName = Algorithms.getFileNameWithoutExtension(toSave);
					if (measurementLayer != null) {
						if (planRouteMode) {
							if (saveType == SaveType.LINE) {
								TrkSegment segment = new TrkSegment();
								if (editingCtx.hasRoute()) {
									segment.points.addAll(editingCtx.getDistinctRoutePoints());
								} else {
									segment.points.addAll(before.points);
									segment.points.addAll(after.points);
								}
								Track track = new Track();
								track.name = trackName;
								track.segments.add(segment);
								gpx.tracks.add(track);
							} else if (saveType == SaveType.ROUTE_POINT) {
								if (editingCtx.hasRoute()) {
									GPXFile newGpx = editingCtx.exportRouteAsGpx(trackName);
									if (newGpx != null) {
										gpx = newGpx;
									}
								}
								gpx.addRoutePoints(points);
							}
						} else if (actionType != null) {
							GpxData gpxData = editingCtx.getGpxData();
							switch (actionType) {
								case ADD_SEGMENT: {
									List<WptPt> snappedPoints = new ArrayList<>();
									snappedPoints.addAll(before.points);
									snappedPoints.addAll(after.points);
									gpx.addTrkSegment(snappedPoints);
									break;
								}
								case ADD_ROUTE_POINTS: {
									gpx.replaceRoutePoints(points);
									break;
								}
								case EDIT_SEGMENT: {
									if (gpxData != null) {
										TrkSegment segment = new TrkSegment();
										segment.points.addAll(points);
										gpx.replaceSegment(gpxData.getTrkSegment(), segment);
									}
									break;
								}
								case OVERWRITE_SEGMENT: {
									if (gpxData != null) {
										List<WptPt> snappedPoints = new ArrayList<>();
										snappedPoints.addAll(before.points);
										snappedPoints.addAll(after.points);
										TrkSegment segment = new TrkSegment();
										segment.points.addAll(snappedPoints);
										gpx.replaceSegment(gpxData.getTrkSegment(), segment);
									}
									break;
								}
							}
						} else {
							gpx.addRoutePoints(points);
						}
					}
					Exception res = null;
					if (!gpx.showCurrentTrack) {
						res = GPXUtilities.writeGpxFile(toSave, gpx);
					}
					savedGpxFile = gpx;
					if (showOnMap) {
						SelectedGpxFile sf = app.getSelectedGpxHelper().selectGpxFile(gpx, true, false);
						if (sf != null) {
							if (actionType == ActionType.ADD_SEGMENT || actionType == ActionType.EDIT_SEGMENT) {
								sf.processPoints(getMyApplication());
							}
						}
					}
					return res;
				}
			}

			@Override
			protected void onPostExecute(Exception warning) {
				onGpxSaved(warning);
			}

			private void onGpxSaved(Exception warning) {
				MapActivity mapActivity = getMapActivity();
				if (mapActivity == null) {
					return;
				}
				if (progressDialog != null && progressDialog.isShowing()) {
					progressDialog.dismiss();
				}
				mapActivity.refreshMap();
				if (warning == null) {
					editingCtx.setChangesSaved();
					if (editingCtx.isNewData() && savedGpxFile != null) {
						QuadRect rect = savedGpxFile.getRect();
						TrkSegment segment = savedGpxFile.getNonEmptyTrkSegment();
						GpxData gpxData = new GpxData(savedGpxFile, rect, ActionType.EDIT_SEGMENT, segment);
						editingCtx.setGpxData(gpxData);
						updateToolbar();
					}
					if (isInEditMode()) {
						dismiss(mapActivity);
					} else {
						switch (saveAction) {
							case SHOW_SNACK_BAR_AND_CLOSE:
								final WeakReference<MapActivity> mapActivityRef = new WeakReference<>(mapActivity);
								snackbar = Snackbar.make(mapActivity.getLayout(),
										MessageFormat.format(getString(R.string.gpx_saved_sucessfully), toSave.getName()),
										Snackbar.LENGTH_LONG)
										.setAction(R.string.shared_string_rename, new OnClickListener() {
											@Override
											public void onClick(View view) {
												MapActivity mapActivity = mapActivityRef.get();
												if (AndroidUtils.isActivityNotDestroyed(mapActivity)) {
													FileUtils.renameFile(mapActivity, toSave, new FileUtils.RenameCallback() {
														@Override
														public void renamedTo(File file) {
														}
													});
												}
											}
										});
								snackbar.getView().<TextView>findViewById(com.google.android.material.R.id.snackbar_action)
										.setAllCaps(false);
								UiUtilities.setupSnackbar(snackbar, nightMode);
								snackbar.show();
								dismiss(mapActivity);
								break;
							case SHOW_IS_SAVED_FRAGMENT:
								SavedTrackBottomSheetDialogFragment.showInstance(mapActivity.getSupportFragmentManager(),
										toSave.getName());
								dismiss(mapActivity);
								break;
							case SHOW_TOAST:
								if (!savedGpxFile.showCurrentTrack) {
									Toast.makeText(mapActivity,
											MessageFormat.format(getString(R.string.gpx_saved_sucessfully), toSave.getAbsolutePath()),
											Toast.LENGTH_LONG).show();
								}
						}
					}
				} else {
					Toast.makeText(mapActivity, warning.getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

	public void updateUndoRedoButton(boolean enable, boolean undo) {
		updateUndoRedoButton(enable, undo ? undoBtn : redoBtn);
	}

	private void updateUndoRedoButton(boolean enable, View view) {
		view.setEnabled(enable);
		int color = enable
				? nightMode ? R.color.icon_color_active_dark : R.color.icon_color_active_light
				: nightMode ? R.color.icon_color_secondary_dark : R.color.icon_color_secondary_light;
		ImageView imageView = ((ImageView) view);
		imageView.setImageDrawable(UiUtilities.tintDrawable(imageView.getDrawable(),
				ContextCompat.getColor(view.getContext(), color)));
	}

	private void enable(View view) {
		view.setEnabled(true);
		view.setAlpha(1);
	}

	private void disable(View view) {
		view.setEnabled(false);
		view.setAlpha(.5f);
	}

	public void updateDistancePointsText() {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			String distanceStr = OsmAndFormatter.getFormattedDistance((float) editingCtx.getRouteDistance(), requireMyApplication());
			distanceTv.setText(distanceStr + ",");
			pointsTv.setText((portrait ? pointsSt + ": " : "") + editingCtx.getPointsCount());
		}
		updateToolbar();
	}

	public void updateToolbar() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity == null) {
			return;
		}
		final GpxData gpxData = editingCtx.getGpxData();
		String fileName = getSuggestedFileName();
		String actionStr = getString(R.string.plan_route);
		boolean editMode = isInEditMode();
		if (editMode) {
			ActionType actionType = gpxData.getActionType();
			switch (actionType) {
				case ADD_ROUTE_POINTS:
					actionStr = getString(R.string.add_route_points);
					break;
				case ADD_SEGMENT:
					actionStr = getString(R.string.add_line);
					break;
				case EDIT_SEGMENT:
				case OVERWRITE_SEGMENT:
					actionStr = getString(R.string.edit_line);
					break;
			}
		}
		if (!editMode && editingCtx.getPointsCount() > 1) {
			toolBarController.setTitle(fileName.replace('_', ' '));
			toolBarController.setDescription(actionStr);
		} else {
			toolBarController.setTitle(actionStr);
			toolBarController.setDescription(null);
		}
		mapActivity.showTopToolbar(toolBarController);
	}

	private void enterMeasurementMode() {
		MapActivity mapActivity = getMapActivity();
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (mapActivity != null && measurementLayer != null) {
			measurementLayer.setInMeasurementMode(true);
			mapActivity.refreshMap();
			mapActivity.disableDrawer();

			AndroidUiHelper.setVisibility(mapActivity, portrait ? View.INVISIBLE : View.GONE,
					R.id.map_left_widgets_panel,
					R.id.map_right_widgets_panel,
					R.id.map_center_info);
			AndroidUiHelper.setVisibility(mapActivity, View.GONE,
					R.id.map_route_info_button,
					R.id.map_menu_button,
					R.id.map_compass_button,
					R.id.map_layers_button,
					R.id.map_search_button,
					R.id.map_quick_actions_button);

			View collapseButton = mapActivity.findViewById(R.id.map_collapse_button);
			if (collapseButton != null && collapseButton.getVisibility() == View.VISIBLE) {
				wasCollapseButtonVisible = true;
				collapseButton.setVisibility(View.INVISIBLE);
			} else {
				wasCollapseButtonVisible = false;
			}
			updateMainIcon();
			updateDistancePointsText();
		}
	}

	private void exitMeasurementMode() {
		MapActivity mapActivity = getMapActivity();
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (mapActivity != null && measurementLayer != null) {
			if (toolBarController != null) {
				mapActivity.hideTopToolbar(toolBarController);
			}
			measurementLayer.setInMeasurementMode(false);
			mapActivity.enableDrawer();

			AndroidUiHelper.setVisibility(mapActivity, View.VISIBLE,
					R.id.map_left_widgets_panel,
					R.id.map_right_widgets_panel,
					R.id.map_center_info,
					R.id.map_route_info_button,
					R.id.map_menu_button,
					R.id.map_compass_button,
					R.id.map_layers_button,
					R.id.map_search_button,
					R.id.map_quick_actions_button);

			View collapseButton = mapActivity.findViewById(R.id.map_collapse_button);
			if (collapseButton != null && wasCollapseButtonVisible) {
				collapseButton.setVisibility(View.VISIBLE);
			}

			mapActivity.refreshMap();
		}
	}

	public void quit(boolean hidePointsListFirst) {
		if (editingCtx.getOriginalPointToMove() != null) {
			cancelMovePointMode();
			return;
		} else if (editingCtx.isInAddPointMode()) {
			cancelAddPointBeforeOrAfterMode();
			return;
		}
		showQuitDialog(hidePointsListFirst);
	}

	private void showQuitDialog(boolean hidePointsListFirst) {
		final MapActivity mapActivity = getMapActivity();
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (mapActivity != null && measurementLayer != null) {
			if (additionalInfoOpened && hidePointsListFirst) {
				collapseAdditionalInfo();
				return;
			}
			if (!editingCtx.hasChanges()) {
				dismiss(mapActivity);
				return;
			}
			ExitBottomSheetDialogFragment.showInstance(mapActivity.getSupportFragmentManager(), this);
		}
	}

	public void dismiss(MapActivity mapActivity) {
		try {
			editingCtx.clearSegments();
			if (additionalInfoOpened) {
				collapseAdditionalInfo();
			}
			resetAppMode();
			hideSnapToRoadIcon();
			if (isInEditMode()) {
				GpxData gpxData = editingCtx.getGpxData();
				GPXFile gpx = gpxData != null ? gpxData.getGpxFile() : null;
				if (gpx != null) {
					Intent newIntent = new Intent(mapActivity, mapActivity.getMyApplication().getAppCustomization().getTrackActivity());
					newIntent.putExtra(TrackActivity.TRACK_FILE_NAME, gpx.path);
					newIntent.putExtra(TrackActivity.OPEN_TRACKS_LIST, true);
					newIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivity(newIntent);
				}
			}
			mapActivity.getSupportFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
		} catch (Exception e) {
			// ignore
		}
	}

	public static boolean showInstance(FragmentManager fragmentManager, LatLon initialPoint) {
		MeasurementToolFragment fragment = new MeasurementToolFragment();
		fragment.setInitialPoint(initialPoint);
		fragment.setPlanRouteMode(true);
		return showFragment(fragment, fragmentManager);
	}

	public static boolean showInstance(FragmentManager fragmentManager, String fileName) {
		MeasurementToolFragment fragment = new MeasurementToolFragment();
		fragment.setFileName(fileName);
		fragment.setPlanRouteMode(true);
		return showFragment(fragment, fragmentManager);
	}

	public static boolean showInstance(FragmentManager fragmentManager, MeasurementEditingContext editingCtx, boolean planRoute) {
		MeasurementToolFragment fragment = new MeasurementToolFragment();
		fragment.setEditingCtx(editingCtx);
		fragment.setPlanRouteMode(planRoute);
		return showFragment(fragment, fragmentManager);
	}

	public static boolean showInstance(FragmentManager fragmentManager, MeasurementEditingContext editingCtx) {
		MeasurementToolFragment fragment = new MeasurementToolFragment();
		fragment.setEditingCtx(editingCtx);
		return showFragment(fragment, fragmentManager);
	}

	public static boolean showInstance(FragmentManager fragmentManager) {
		MeasurementToolFragment fragment = new MeasurementToolFragment();
		fragment.setPlanRouteMode(true);
		return showFragment(fragment, fragmentManager);
	}

	private static boolean showFragment(MeasurementToolFragment fragment, FragmentManager fragmentManager) {
		try {
			fragment.setRetainInstance(true);
			fragmentManager.beginTransaction()
					.add(R.id.bottomFragmentContainer, fragment, MeasurementToolFragment.TAG)
					.commitAllowingStateLoss();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private class MeasurementToolBarController extends TopToolbarController {

		MeasurementToolBarController() {
			super(TopToolbarControllerType.MEASUREMENT_TOOL);
			setBackBtnIconClrIds(0, 0);
			setTitleTextClrIds(R.color.text_color_tab_active_light, R.color.text_color_tab_active_dark);
			setDescrTextClrIds(R.color.text_color_tab_active_light, R.color.text_color_tab_active_dark);
			setBgIds(R.drawable.gradient_toolbar, R.drawable.gradient_toolbar,
					R.drawable.gradient_toolbar, R.drawable.gradient_toolbar);
			setCloseBtnVisible(false);
			setSaveViewVisible(true);
			setSingleLineTitle(false);
			setSaveViewTextId(R.string.shared_string_done);
		}

		@Override
		public void updateToolbar(TopToolbarView view) {
			super.updateToolbar(view);
			setupDoneButton(view);
			View shadow = view.getShadowView();
			if (shadow != null) {
				shadow.setVisibility(View.GONE);
			}
		}

		private void setupDoneButton(TopToolbarView view) {
			TextView done = view.getSaveView();
			Context ctx = done.getContext();
			done.setAllCaps(false);
			ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) done.getLayoutParams();
			layoutParams.height = ctx.getResources().getDimensionPixelSize(R.dimen.measurement_tool_button_height);
			layoutParams.leftMargin = ctx.getResources().getDimensionPixelSize(R.dimen.context_menu_padding_margin_large);
			layoutParams.rightMargin = ctx.getResources().getDimensionPixelSize(R.dimen.context_menu_padding_margin_large);
			int paddingH = ctx.getResources().getDimensionPixelSize(R.dimen.context_menu_padding_margin_large);
			int paddingV = ctx.getResources().getDimensionPixelSize(R.dimen.context_menu_padding_margin_small);
			done.setPadding(paddingH, paddingV, paddingH, paddingV);
			AndroidUtils.setBackground(ctx, done, nightMode, R.drawable.purchase_dialog_outline_btn_bg_light,
					R.drawable.purchase_dialog_outline_btn_bg_dark);
		}

		@Override
		public int getStatusBarColor(Context context, boolean night) {
			return NO_COLOR;
		}
	}

	@Override
	public void onGpxApproximationDone(GpxRouteApproximation gpxApproximation, ApplicationMode mode) {
		MeasurementToolLayer measurementLayer = getMeasurementLayer();
		if (measurementLayer != null) {
			editingCtx.setInApproximationMode(true);
			ApplyGpxApproximationCommand command = new ApplyGpxApproximationCommand(measurementLayer, gpxApproximation, mode);
			if (!editingCtx.getCommandManager().update(command)) {
				editingCtx.getCommandManager().execute(command);
			}
			if (additionalInfoOpened) {
				collapseAdditionalInfo();
			}
			updateSnapToRoadControls();
		}
	}

	@Override
	public void onApplyGpxApproximation() {
		exitApproximationMode();
		doAddOrMovePointCommonStuff();
		if (directionMode) {
			directionMode = false;
			MapActivity mapActivity = getMapActivity();
			if (mapActivity != null) {
				if (editingCtx.hasRoute()) {
					String trackName = getSuggestedFileName();
					GPXFile gpx = editingCtx.exportRouteAsGpx(trackName);
					if (gpx != null) {
						ApplicationMode appMode = editingCtx.getAppMode();
						dismiss(mapActivity);
						runNavigation(gpx, appMode);
					} else {
						Toast.makeText(mapActivity, getString(R.string.error_occurred_saving_gpx), Toast.LENGTH_SHORT).show();
					}
				} else {
					Toast.makeText(mapActivity, getString(R.string.error_occurred_saving_gpx), Toast.LENGTH_SHORT).show();
				}
			}
		}
	}

	@Override
	public void onCancelGpxApproximation() {
		editingCtx.getCommandManager().undo();
		exitApproximationMode();
		directionMode = false;
		updateSnapToRoadControls();
		updateToolbar();
	}

	private void exitApproximationMode() {
		editingCtx.setInApproximationMode(false);
		MeasurementToolLayer layer = getMeasurementLayer();
		if (layer != null) {
			layer.setTapsDisabled(false);
		}
	}

	public interface OnUpdateAdditionalInfoListener {
		void onUpdateAdditionalInfo();
	}
}
