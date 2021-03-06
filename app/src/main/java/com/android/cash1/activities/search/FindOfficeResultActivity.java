package com.android.cash1.activities.search;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cash1.R;
import com.android.cash1.model.Cash1Activity;
import com.android.cash1.model.Office;
import com.android.cash1.rest.Cash1ApiService;
import com.android.cash1.rest.Cash1Client;
import com.android.cash1.rest.GeofenceObject;
import com.android.cash1.rest.LocationApiService;
import com.android.cash1.rest.LocationClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class FindOfficeResultActivity extends Cash1Activity implements
        ConnectionCallbacks, OnConnectionFailedListener {

    protected static final String TAG = "basic-location-sample";

    private LatLng mCoordinates;

    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";
    // Bool to track whether the app is already resolving an error
    private boolean mResolvingError = false;

    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    /**
     * Represents a geographical location.
     */
    protected Location mLastLocation;

    public static final int RESULT_NOT_FOUND = -101;
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private String mWhereToSearch;
    private String mZipCode;
    private String mAddress;
    private String mCity;
    private String mState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_office_result);

        setupActionBar();
        setupFooter();


        mZipCode = getIntent().getStringExtra("zipcode_string");
        mAddress = getIntent().getStringExtra("address");
        mCity = getIntent().getStringExtra("city");
        mState = getIntent().getStringExtra("state");
        mWhereToSearch = getIntent().getStringExtra("where_to_search");

        TextView queryTextView = (TextView) findViewById(R.id.query_textview);

        switch (mWhereToSearch) {
            case "currentlocation":
                queryTextView.setText("Current location" + "\n");
                buildGoogleApiClient();
                break;
            case "zipcode":
                queryTextView.setText("ZIP code “" + mZipCode + "”" + "\n");
                preloadCoordinates(mZipCode);
                break;
            case "cityaddressstate":
                queryTextView.setText(mAddress + ", " + mCity + ", " + mState + "\n");
                preloadCoordinates(mAddress + ", " + mCity + ", " + mState);
                break;
        }

        setUpMap();

        mResolvingError = (savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false));
    }

    private void preloadCoordinates(String address) {
        LocationApiService service = new LocationClient().getApiService();
        service.getCoordinatesForAddress(address, new Callback<GeofenceObject>() {
            @Override
            public void success(GeofenceObject geofenceObject, Response response) {
                if (geofenceObject.getStatus().equals("OK")) {
                    JsonElement coordinatesObject = geofenceObject.getContentObjects().get(0).getAsJsonObject("geometry").get("location");
                    double latitude = coordinatesObject.getAsJsonObject().getAsJsonPrimitive("lat").getAsDouble();
                    double longitude = coordinatesObject.getAsJsonObject().getAsJsonPrimitive("lng").getAsDouble();
                    mCoordinates = new LatLng(latitude, longitude);

                    listAllStores();
                } else {
                    setResult(RESULT_NOT_FOUND);
                    finish();
                }
            }

            @Override
            public void failure(RetrofitError error) {
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    /**
     * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        // Provides a simple way of getting a device's location and is well suited for
        // applications that do not require a fine-grained location and that do not need location
        // updates. Gets the best and most recent location currently available, which may be null
        // in rare cases when a location is not available.
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (getLastLatitude() != -1 && getLastLongitude() != -1) {
            if (mLastLocation != null) {
                SharedPreferences sharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(this);
                sharedPreferences.edit()
                        .putFloat("latitude", (float) mLastLocation.getLatitude())
                        .putFloat("longitude", (float) mLastLocation.getLongitude())
                        .apply();
            }
            listAllStores();
        } else {
            Toast.makeText(this, R.string.no_location_detected, Toast.LENGTH_LONG).show();
            mGoogleApiClient.disconnect();
            finish();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            // Show dialog using GooglePlayServicesUtil.getErrorDialog()
            showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }
    }

    // The rest of this code is all about building the error dialog

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), "errordialog");
    }

    private static final String GooglePlayStorePackageNameOld = "com.google.market";
    private static final String GooglePlayStorePackageNameNew = "com.android.vending";

    private boolean isGooglePlayStoreAppInstalled() {
        PackageManager packageManager = getApplication().getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(0);
        for (PackageInfo packageInfo : packages) {
            if (packageInfo.packageName.equals(GooglePlayStorePackageNameOld) ||
                    packageInfo.packageName.equals(GooglePlayStorePackageNameNew)) {
                return true;
            }
        }
        return false;
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
        if (!isGooglePlayStoreAppInstalled()) {
            Toast.makeText(this, "Failed to install Google Play Services. " +
                    "You need to manually install Play Store app.", Toast.LENGTH_LONG).show();
        }
        finish();
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode,
                    this.getActivity(), REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((FindOfficeResultActivity) getActivity()).onDialogDismissed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mGoogleApiClient.isConnecting() &&
                        !mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.connect();
                }
            }
        }
    }

    private static final String STATE_RESOLVING_ERROR = "resolving_error";

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    private void listAllStores() {
        Cash1ApiService service = new Cash1Client().getApiService();
        service.listAllOffices(new Callback<List<Office>>() {
            @Override
            public void success(List<Office> officeList, Response response) {
                findViewById(R.id.loading).setVisibility(View.GONE);

                List<Office> sortedList = null;

                switch (mWhereToSearch) {
                    case "currentlocation":
                        sortedList = sortByDistanceToMe(officeList);
                        break;
                    case "zipcode":
                    case "cityaddressstate":
                        sortedList = sortByDistanceToAddress(officeList);
                        break;
                }

                if (sortedList == null || sortedList.size() == 0) {
                    findViewById(R.id.loading).setVisibility(View.VISIBLE);
                    setResult(RESULT_NOT_FOUND);
                    finish();
                    return;
                }

                if (mMap != null) {
                    displayMarkersOnMap(sortedList);
                }

                LinearLayout container = (LinearLayout) findViewById(R.id.list_container);
                for (int i = 0; i < sortedList.size(); i++) {
                    final Office office = sortedList.get(i);

                    FrameLayout listItemContainer = (FrameLayout) View.inflate(
                            FindOfficeResultActivity.this, R.layout.office_list_item, null);

                    TextView positionTextView = (TextView) listItemContainer.findViewById(R.id.position);
                    positionTextView.setText((i + 1) + "");

                    TextView streetTextView = (TextView) listItemContainer.findViewById(R.id.street);
                    Spanned street;
                    if (mZipCode == null) {
                        street = highlightMatches(office.getStreet());
                    } else {
                        street = Html.fromHtml(office.getStreet());
                    }
                    streetTextView.setText(street, TextView.BufferType.SPANNABLE);

                    TextView addressTextView = (TextView) listItemContainer.findViewById(R.id.address);
                    Spanned address = highlightMatches(office.getAddress());
                    addressTextView.setText(address, TextView.BufferType.SPANNABLE);


                    TextView distanceTextView = (TextView) listItemContainer.findViewById(R.id.distance_to);
                    String distanceString;
                    if (mWhereToSearch.equals("currentlocation")) {
                        double distance = getDistanceToMe(office);
                        if (distance <= 99) {
                            distanceString = String.format("%.1f", distance) + " mi";
                        } else {
                            distanceString = String.format("%.0f", distance) + " mi";
                        }
                    } else {
                        double distance = getDistanceToAddress(office);
                        if (distance <= 99) {
                            distanceString = String.format("%.1f", distance) + " mi";
                        } else {
                            distanceString = String.format("%.0f", distance) + " mi";
                        }
                    }
                    distanceTextView.setText(distanceString);

                    int distance;
                    if (distanceString.contains(".")) {
                        distance = Integer.parseInt(distanceString.split("\\.")[0]);
                    } else {
                        distance = Integer.parseInt(distanceString.replace(" mi", ""));
                    }

                    if (distance < 4.2) {
                        View itemContainer = listItemContainer.findViewById(R.id.container);
                        itemContainer.setBackgroundColor(Color.parseColor("#5940d47e"));
                        distanceTextView.setText(getString(R.string.very_close));
                        listItemContainer.findViewById(R.id.divider).setVisibility(View.GONE);
                    }


                    if (i + 1 == sortedList.size()) {
                        listItemContainer.findViewById(R.id.divider).setVisibility(View.GONE);
                    }

                    listItemContainer.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(FindOfficeResultActivity.this, OfficeDetailActivity.class);
                            intent.putExtra("store_id", office.getId());
                            intent.putExtra("latitude", office.getLatitude());
                            intent.putExtra("longitude", office.getLongitude());
                            startActivity(intent);
                        }
                    });

                    container.addView(listItemContainer);

                    findViewById(R.id.loading).setVisibility(View.GONE);
                }
            }

            @Override
            public void failure(RetrofitError error) {
            }
        });
    }

    /**
     * Sorts stores by distance from current location
     */
    private List<Office> sortByDistanceToMe(List<Office> officeList) {
        Collections.sort(officeList, new Comparator<Office>() {
            @Override
            public int compare(Office lhs, Office rhs) {
                return (int) (getDistanceToMe(lhs) - getDistanceToMe(rhs));
            }
        });
        return officeList;
    }

    private double getDistanceToMe(Office office) {
        Location myLocation = new Location("point A");
        myLocation.setLatitude(getLastLatitude());
        myLocation.setLongitude(getLastLongitude());

        Location officeLocation = new Location("point B");
        officeLocation.setLatitude(office.getLatitude());
        officeLocation.setLongitude(office.getLongitude());

        double milesPerMeter = 0.000621371;
        double distanceInMiles = myLocation.distanceTo(officeLocation) * milesPerMeter;
        return distanceInMiles;
    }

    /**
     * Sorts stores by distance from specified address
     */
    private List<Office> sortByDistanceToAddress(List<Office> officeList) {
        Collections.sort(officeList, new Comparator<Office>() {
            @Override
            public int compare(Office lhs, Office rhs) {
                return (int) (getDistanceToAddress(lhs) - getDistanceToAddress(rhs));
            }
        });
        return officeList;
    }

    private double getDistanceToAddress(Office office) {
        if (mCoordinates == null) return 0;

        Location specifiedLocation = new Location("point A");
        specifiedLocation.setLatitude(mCoordinates.latitude);
        specifiedLocation.setLongitude(mCoordinates.longitude);

        Location officeLocation = new Location("point B");
        officeLocation.setLatitude(office.getLatitude());
        officeLocation.setLongitude(office.getLongitude());

        double milesPerMeter = 0.000621371;
        double distanceInMiles = specifiedLocation.distanceTo(officeLocation) * milesPerMeter;
        return distanceInMiles;
    }

    private Spanned highlightMatches(String string) {
        if (mZipCode != null) {
            string = string.replaceAll("(?i)" + mZipCode, "<font color='red'>" + mZipCode + "</font>");
        }
        if (mAddress != null && mCity != null && mState != null) {
            string = string.replaceAll("(?i)" + mAddress, "<font color='red'>" + mAddress + "</font>");
            string = string.replaceAll("(?i)" + mCity, "<font color='red'>" + mCity + "</font>");
            string = string.replaceAll("(?i)" + mState, "<font color='red'>" + mState + "</font>");
        }
        return Html.fromHtml(string);
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMap() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            int availabilityCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
            if (availabilityCode != ConnectionResult.SUCCESS) {
                View mapFragmentView = (getSupportFragmentManager().findFragmentById(R.id.map)).getView();
                if (mapFragmentView != null) {
                    mapFragmentView.setVisibility(View.GONE);
                }
            } else {
                mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                        .getMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */

    private void displayMarkersOnMap(List<Office> officeList) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (Office office : officeList) {
            Marker marker = mMap.addMarker(new MarkerOptions().position(new LatLng(
                    office.getLatitude(), office.getLongitude())).title(office.getAddress()));

            builder.include(marker.getPosition());
        }
        LatLngBounds bounds = builder.build();

        int padding = 0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);

        if (officeList.size() == 1) {
            Office office = officeList.get(0);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(office.getLatitude(), office.getLongitude()), 14.0f));
        } else {
            mMap.moveCamera(cu);
        }
    }
}
