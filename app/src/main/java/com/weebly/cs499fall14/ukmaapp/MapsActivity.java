package com.weebly.cs499fall14.ukmaapp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapsActivity extends FragmentActivity implements LocationListener, LocationSource
{
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private LocationSource.OnLocationChangedListener mListener;
    private LocationManager locationManager;
    private HashMap<String, Building> mBuildingHash = new HashMap<String, Building>();
    private ArrayList<Marker> mMarkerArray = new ArrayList<Marker>();
    MarkerOptions markerOptions;
    LatLng latLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        setUpMapIfNeeded();
        setUpMarkers();
    }

    @Override
    public void onPause() {
        if (locationManager != null) {
            locationManager.removeUpdates(this);
        }

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        if (locationManager != null) {
            mMap.setMyLocationEnabled(true);
        }
    }

    // Change markers button listener
    public void changeMarkersListener(View view) {
        for (Marker m : mMarkerArray) {
            m.setAlpha(1.0f - m.getAlpha()); // toggle transparency
        }
    }

    // Search button listener
    public void searchListener(View view) {
        // Getting reference to EditText to get the user input location
        EditText etLocation = (EditText) findViewById(R.id.et_location);

        // Getting user input location
        String location = etLocation.getText().toString();

        if (location != null && !location.equals("")) {
            new GeocoderTask().execute(location);
        }
    }

    // An AsyncTask class for accessing the GeoCoding Web Service
    private class GeocoderTask extends AsyncTask<String, Void, List<Address>> {

        @Override
        protected List<Address> doInBackground(String... locationName) {
            // Creating an instance of Geocoder class
            Geocoder geocoder = new Geocoder(getBaseContext());
            List<Address> addresses = null;

            try {
                // Getting a maximum of 3 Address that matches the input text
                addresses = geocoder.getFromLocationName(locationName[0], 3);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return addresses;
        }

        @Override
        protected void onPostExecute(List<Address> addresses) {
            if (addresses==null || addresses.size()==0) {
                Toast.makeText(getBaseContext(), "No Location found", Toast.LENGTH_SHORT).show();
            }

            // Clears all the existing markers on the map
            mMap.clear();

            // Adding Markers on Google Map for each matching address
            for (int i=0; i<addresses.size(); i++) {
                Address address = (Address) addresses.get(i);

                // Creating an instance of GeoPoint, to display in Google Map
                latLng = new LatLng(address.getLatitude(), address.getLongitude());

                //This is the address they entered
                /*String addressText = String.format("%s, %s",
                        address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "",
                        address.getCountryName());*/

                //Attempt to fix the NULL for not entering in country
                //This takes care of the NULL Value, but it does not register the city/state/country
                String addressText = String.format("%s, Lexington KY USA",
                        address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "Lexington, KY, USA");

                markerOptions = new MarkerOptions();
                markerOptions.position(latLng);
                markerOptions.title(addressText);

                mMap.addMarker(markerOptions);

                // Locate the first location and centers
                if (i==0) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                }
            }
        }
    }

    public class Building {
        public String name;
        public String code;
        public double lat;
        public double lng;
        public String url;

        /* Constuctors are cool. This one will fill the fields based on the
           incoming array "tokens" which represents a row from the table where
           each index is a column. example
           tokens[x] = {White Hall, CB, 38.038079, -84.503844, http://www.whitehall.com/}

         * Once the fields are filled the Building is hashed by its name so it can be
           searched for later like this: buildingHash.get("White Hall")

         * NOTE: It will only hash the object if lat and lng are given and numeric */
        Building(String[] tokens) {
            name = "Error";
            switch (tokens.length) {
                case 1: break;
                case 2: break;
                case 3: break;
                case 4: if (isNumeric(tokens[2]) && isNumeric(tokens[3])) {
                            name = tokens[0];
                            code = tokens[1];
                            lat = Double.parseDouble(tokens[2]);
                            lng = Double.parseDouble(tokens[3]);
                        }
                        break;
                case 5: if (isNumeric(tokens[2]) && isNumeric(tokens[3])) {
                            name = tokens[0];
                            code = tokens[1];
                            lat = Double.parseDouble(tokens[2]);
                            lng = Double.parseDouble(tokens[3]);
                            url = tokens[4];
                        }
                        break;
                default: break;
            }
            if (!name.equals("Error")) {
                mBuildingHash.put(name, this);
            }
        }
    }

    private void setUpMarkers() {
        BufferedReader reader = null;
        try {
            // Open the .csv (comma separated value) file
            reader = new BufferedReader(new InputStreamReader(getAssets().open("Buildings.csv")));

            String mLine = reader.readLine(); // read first line
            while (mLine != null) {
                // Split lines (by commas) into tokens, example
                // tokens[x] = {White Hall,CB,38.038079,-84.503844,http://www.whitehall.com/}
                String[] tokens = mLine.split(",");

                // The constructor fills up the Building and hashes it by its name
                Building bldg = new Building(tokens);

                // Create a marker for the current Building and add it to the map
                if (!bldg.name.equals("Error")) {
                    Marker mMarker = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(bldg.lat, bldg.lng))
                            .title(bldg.name) // this is what we search clicked markers by so beware
                            .alpha(1.0f) // 0.0 (invisible) - 1.0 (fully visible)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)) // make pin blue
                            .snippet(bldg.code + " Hours: 8-5")); // We will just add a new column for hours of operation
                    mMarkerArray.add(mMarker); // This is so we can loop through markers later
                }
                mLine = reader.readLine(); // increment line
            }
        } catch (IOException e) {
            //error opening file probably
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    //error closing file probably
                }
            }
        }

        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                // Search hash by marker title (which is a name) then take the object's url
                String url = mBuildingHash.get(marker.getTitle()).url;

                if (url != null) {
                    Intent openUrl = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(openUrl);
                }
            }
        });
    }

    public static boolean isNumeric(String str) {
        try {
            double d = Double.parseDouble(str);
        } catch(NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     *
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     *
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
            mMap.setLocationSource(this);
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera.
     *
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        // For ping location
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            boolean gpsIsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean networkIsEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (gpsIsEnabled) {
                //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 10F, this);
            } else if (networkIsEnabled) {
                //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 10F, this);
            } else {
                //Show some kind of message that tells the user that the GPS is disabled.
            }
        } else {
            //Show some generic error dialog because something must have gone wrong with location manager.
        }
        //LocationListener locLister = new MyLocationLister();
        //locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locLister);

        // Set zoom and tilt
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(38.038024, -84.504686))
                .zoom(18)
                .tilt(30) // Sets the tilt of the camera to 30 degrees
                .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        // Add Ground Overlay
        GroundOverlayOptions campusMap = new GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromResource(R.drawable.ic_launcher))
                .position(new LatLng(38.038024, -84.504686), 8600f, 6500f);
        mMap.addGroundOverlay(campusMap);

        // Change to Satellite to get rid of labels
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

        // Added for locator button
        mMap.setMyLocationEnabled(true);
    }
    @Override
    public void activate(OnLocationChangedListener listener)
    {
        mListener = listener;
    }

    @Override
    public void deactivate()
    {
        mListener = null;
    }

    @Override
    public void onLocationChanged(Location location) {
        if( mListener != null ) {
            mListener.onLocationChanged( location );
            mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
        Toast.makeText(this, "provider disabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
        Toast.makeText(this, "provider enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
        Toast.makeText(this, "status changed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    //sets 3D buildings on if "true"
    public final void setBuildingsEnabled (){
    }
}

