package com.example.mapdemo;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.Toast;


import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.ActionBar;
import android.os.Bundle;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.RuntimePermissions;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

@RuntimePermissions
public class MapDemoActivity extends AppCompatActivity {

    //veille
    private PowerManager.WakeLock wl;

    private SupportMapFragment mapFragment;
    private GoogleMap map;
    private LocationRequest mLocationRequest;
    Location mCurrentLocation;
    private long UPDATE_INTERVAL = 1 ;  /* 60000 60 secs */
    private long FASTEST_INTERVAL = 1000 ; /* 5000 5 secs */
    private String etatCourse = "enAttente"; //enCour, enAttente, Fini, enPause

    //trace dur la map
    private ArrayList<PolylineOptions> maTrace = new ArrayList<PolylineOptions>();
    //private PolylineOptions maTrace = new PolylineOptions();

    private Button suivi; //activer ou désactiver le suivi
    boolean suiviPos = true;

    //temps
    private float tempActivite = 0;
    private Date dateDebut ;

    //private Button start, pause, fin;
    private Chronometer chronometer;
    private long pauseOffset;

    private final static String KEY_LOCATION = "location";

    /*
     * Define a request code to send to Google Play services This code is
     * returned in Activity.onActivityResult
     */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
/////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map_demo_activity);
        if (TextUtils.isEmpty(getResources().getString(R.string.google_maps_api_key))) {
            throw new IllegalStateException("You forgot to supply a Google Maps API key");
        }

        if (savedInstanceState != null && savedInstanceState.keySet().contains(KEY_LOCATION)) {
            // Since KEY_LOCATION was found in the Bundle, we can be sure that mCurrentLocation
            // is not null.
            mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION);
        }

        mapFragment = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map));
        if (mapFragment != null) {
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap map) {
                    loadMap(map);
                }
            });
        } else {
            //Toast.makeText(this, "Error - Map Fragment was null!!", Toast.LENGTH_SHORT).show();
        }
        //veille
        /*
        PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.ON_AFTER_RELEASE, this.getClass().getName());
        */
        //Chronomètre
        this.chronometer = findViewById(R.id.chronometer);
    }
    /////////////////////////////////////////////////////
    public void startChronometer(View v){
        if(this.etatCourse != "enCour"){
            this.maTrace.add(new PolylineOptions());
            this.dateDebut = new Date();
            this.chronometer.setBase(SystemClock.elapsedRealtime() - this.pauseOffset);
            this.chronometer.start();
            this.etatCourse = "enCour";
        }
    }
    public void pauseChronometer(View v){
        if(this.etatCourse == "enCour"){
            this.chronometer.stop();
            //
            Date dateMtn =  new Date();
            long date1 = this.dateDebut.getTime();
            long date2 = dateMtn.getTime();
            long temp = date2 - date1;
            float temp2 = temp / 3600000.0f;
            this.tempActivite = this.tempActivite + (temp2*60);
            this.pauseOffset = SystemClock.elapsedRealtime() - this.chronometer.getBase();
            //
            this.etatCourse = "enPause";
        }
    }
    public void restChronometer(View v){
        this.chronometer.stop();
        // pas oublier de prendre le screen

        //Chrono de l'activité
        //String tempsFinal = SystemClock.elapsedRealtime() - this.pauseOffset+" ";
        this.chronometer.setBase(SystemClock.elapsedRealtime() - this.pauseOffset);
        //
        Date dateMtn = new Date();
        long date1 = this.dateDebut.getTime();
        long date2 = dateMtn.getTime();
        long temp = date2 - date1;
        float temp2 = temp / 3600000.0f;
        this.tempActivite = this.tempActivite + (temp2*60);

        Toast.makeText(this, tempActivite+"", Toast.LENGTH_SHORT).show();
        //
        this.etatCourse = "Fini";
        this.chronometer.setBase(SystemClock.elapsedRealtime());
        this.pauseOffset = 0;

        //calcule la distance parcourue
        float distance = 0;

        for(int i = 0; i < this.maTrace.size(); i++){
            for(int b = 0; b < this.maTrace.get(i).getPoints().size()-1; b++){
                distance = distance + this.getDistance(new LatLng(this.maTrace.get(i).getPoints().get(b).latitude,this.maTrace.get(i).getPoints().get(b).longitude)
                        ,new LatLng(this.maTrace.get(i).getPoints().get(b+1).latitude,this.maTrace.get(i).getPoints().get(b+1).longitude));
            }
        }
        Toast.makeText(this, distance+"", Toast.LENGTH_SHORT).show();
        /*for (int i = 0; i < this.maTrace.getPoints().size()-1; i++) {
            distance = distance + this.getDistance(new LatLng(this.maTrace.getPoints().get(i).latitude,this.maTrace.getPoints().get(i).longitude)
                    ,new LatLng(this.maTrace.getPoints().get(i+1).latitude,this.maTrace.getPoints().get(i+1).longitude));
        }
        Toast.makeText(this, distance+"", Toast.LENGTH_SHORT).show();
        //clear le trace
        this.maTrace.getPoints().clear();*/
        // pas oublier de prendre le screen
        this.maTrace.clear();
        this.map.clear();


    }
    ///////////////////////////////////
    private float getDistance(LatLng my_latlong, LatLng frnd_latlong) {
        Location l1 = new Location("One");
        l1.setLatitude(my_latlong.latitude);
        l1.setLongitude(my_latlong.longitude);

        Location l2 = new Location("Two");
        l2.setLatitude(frnd_latlong.latitude);
        l2.setLongitude(frnd_latlong.longitude);

        //distance M
        float distance = l1.distanceTo(l2);
        //distance km
        distance = distance / 1000.0f;

        return distance;
    }
    ///////////////////////////
    // Permet de suivre la postion
    public void suiviPosition (View v){
        if(this.suiviPos == true ){
            this.suiviPos = false;
        }
        else {
            this.suiviPos = true;
        }
    }
//////////////////////////////////////////////////////////////////////////////////////////////////
    protected void loadMap(GoogleMap googleMap) {
        map = googleMap;
        if (map != null) {
            // Map is ready
            //Toast.makeText(this, "Map Fragment was loaded properly!", Toast.LENGTH_SHORT).show();
            MapDemoActivityPermissionsDispatcher.getMyLocationWithPermissionCheck(this);
            MapDemoActivityPermissionsDispatcher.startLocationUpdatesWithPermissionCheck(this);
        } else {
            //Toast.makeText(this, "Error - Map was null!!", Toast.LENGTH_SHORT).show();
        }
    }
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MapDemoActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @SuppressWarnings({"MissingPermission"})
    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void getMyLocation() {
        map.setMyLocationEnabled(true);

        FusedLocationProviderClient locationClient = getFusedLocationProviderClient(this);
        locationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            onLocationChanged(location);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("MapDemoActivity", "Error trying to get last GPS location");
                        e.printStackTrace();
                    }
                });
    }

    /*
     * Called when the Activity becomes visible.
    */
    @Override
    protected void onStart() {
        super.onStart();
       /* if (wl != null) {
            wl.acquire();
        }*/
    }

    /*
     * Called when the Activity is no longer visible.
	 */
    @Override
    protected void onStop() {
        super.onStop();
        /*if (wl != null) {
            wl.release();
        }*/
    }

    private boolean isGooglePlayServicesAvailable() {
        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d("Location Updates", "Google Play services is available.");
            return true;
        } else {
            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                    CONNECTION_FAILURE_RESOLUTION_REQUEST);

            // If Google Play services can provide an error dialog
            if (errorDialog != null) {
                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();
                errorFragment.setDialog(errorDialog);
                errorFragment.show(getSupportFragmentManager(), "Location Updates");
            }

            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display the connection status

        if (mCurrentLocation != null) {
            //Toast.makeText(this, "GPS location was found!", Toast.LENGTH_SHORT).show();
            LatLng latLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
            map.animateCamera(cameraUpdate);
        } else {
            //Toast.makeText(this, "Current location was null, enable GPS on emulator!", Toast.LENGTH_SHORT).show();
        }
        MapDemoActivityPermissionsDispatcher.startLocationUpdatesWithPermissionCheck(this);
    }

    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    protected void startLocationUpdates() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);
        //noinspection MissingPermission
        getFusedLocationProviderClient(this).requestLocationUpdates(mLocationRequest, new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        onLocationChanged(locationResult.getLastLocation());
                    }
                },
                Looper.myLooper());

    }
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public void onLocationChanged(Location location) {
        // GPS may be turned off
        if (location == null) {
            return;
        }

        // Report to the UI that the location was updated

        mCurrentLocation = location;
        String msg = "Updated Location: " +
                Double.toString(location.getLatitude()) + "," +
                Double.toString(location.getLongitude());
        //Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        //Actualise la map
        if (mCurrentLocation != null) {
            if(this.suiviPos == true){
                //Toast.makeText(this, "GPS location was found!", Toast.LENGTH_SHORT).show();
                LatLng latLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 17);
                map.animateCamera(cameraUpdate);
            }


            //marker à utiliser pour voir le parcourt
            if(this.etatCourse == "enCour"){

                this.maTrace.get(this.maTrace.size()-1).add(new LatLng(mCurrentLocation.getLatitude(),mCurrentLocation.getLongitude()));
                //this.maTrace.add(new LatLng(mCurrentLocation.getLatitude(),mCurrentLocation.getLongitude()));
                this.map.clear();

                for(int i = 0 ; i < this.maTrace.size() ; i++){
                   this.map.addPolyline(this.maTrace.get(i));
                }



                /*
                HashMap<String, String> markers = new HashMap<>();
                Marker marker = map.addMarker(new MarkerOptions().position(new LatLng(mCurrentLocation.getLatitude(),mCurrentLocation.getLongitude())).title("marker").snippet(""));
                //marker.setVisible(false);
                i = i+1;
                markers.put(marker.getId(),""+this.i);
                */
            }


        } else {
            //Toast.makeText(this, "Current location was null, enable GPS on emulator!", Toast.LENGTH_SHORT).show();
        }
    }
////////////////////////////////////////////////////////////////////////////////////
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putParcelable(KEY_LOCATION, mCurrentLocation);
        super.onSaveInstanceState(savedInstanceState);
    }

    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends android.support.v4.app.DialogFragment {

        // Global field to contain the error dialog
        private Dialog mDialog;

        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }
///////////////////////////////////////////////////////////////////////////////////////////////////////
}
