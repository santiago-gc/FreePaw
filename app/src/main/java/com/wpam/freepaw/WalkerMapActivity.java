package com.wpam.freepaw;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class WalkerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;

    private Button mLogout, mStart;

    /////
    private TextView wID;
    private TextView wLoc;
    private int conta = 0;
    private String cusId = "", confirmed = "false", Fare="1";

    /////

    private String customerId = "";

    private Boolean isLoggingOut = false;

    private SupportMapFragment mapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walker_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(WalkerMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }else{
            mapFragment.getMapAsync(this);
        }

        ///
        wID = (TextView)findViewById(R.id.textwalkerID);
        wLoc = (TextView)findViewById(R.id.textwalkerLoc);
        ///

        mLogout = (Button) findViewById(R.id.logout);
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLoggingOut = true;
                disconnectWalker();

                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(WalkerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        mStart = (Button) findViewById(R.id.start);
        mStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                disconnectWalker();
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("walkersAvailable");
                Toast.makeText(getApplicationContext(), "Confirmed by customer, enjoy!", Toast.LENGTH_LONG).show();

*/
                if (mStart.getText()=="done!"){
                    mStart.setVisibility(View.GONE);
                }
                Toast.makeText(getApplicationContext(), "Enjoy the walk, time: "+Fare+" hrs", Toast.LENGTH_LONG).show();
                final int time = Integer.parseInt(Fare)*10000;
                new CountDownTimer(time, 1000) {

                    public void onTick(long millisUntilFinished) {
                        mStart.setText("Time remaining: " + millisUntilFinished / 1000);
                        mStart.setBackgroundColor(Color.GREEN);
                        if (millisUntilFinished<time/3){
                            mStart.setText("Time is running out: " + millisUntilFinished / 1000);
                            mStart.setBackgroundColor(Color.RED);
                        }
                    }

                    public void onFinish() {
                        mStart.setText("done!");
                        mStart.setBackgroundColor(Color.RED);
                    }

                }.start();


                //mStart.setText("Started at: "+new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date())+"\tEnds at: "+ );
                return;
            }
        });

        //Toast.makeText(getApplicationContext(), "ON CREATE", Toast.LENGTH_LONG).show();
        getAssignedCustomer();
    }

    private void getAssignedCustomer(){
        String walkerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Walkers").child(walkerId);
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    customerId = dataSnapshot.getValue().toString();
                    getAssignedCustomerPickUpLocation();
                    ///
                    conta += 1;
                    wID.setText("walker ID: " + FirebaseAuth.getInstance().getCurrentUser().getUid() + " -ins- "+ conta);//String.valueOf(
                    //wID.setText(wID.getText()+ "*");//String.valueOf(

                    ///
                }else{
                    customerId = "";
                    if(pickupMarker != null){
                        pickupMarker.remove();
                    }
                    if(assignedCustomerPickUpLocationRefListener != null){
                        assignedCustomerPickUpLocationRef.removeEventListener(assignedCustomerPickUpLocationRefListener);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    //GET PICK UP LOCATION
    Marker pickupMarker;
    private DatabaseReference assignedCustomerPickUpLocationRef;
    private ValueEventListener assignedCustomerPickUpLocationRefListener;
    private void getAssignedCustomerPickUpLocation(){
        //customerId = FirebaseDatabase.getInstance().getReference().child("customerRequest").getKey();
        //

         final List<String> userIdList = new ArrayList();
         FirebaseDatabase.getInstance().getReference().child("customerRequest").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                //Toast.makeText(getApplicationContext(), dataSnapshot.toString(), Toast.LENGTH_LONG).show();

                //cusId=dataSnapshot.getChildren().toString();
                //cusId = dataSnapshot.getValue().toString();
                //cusId = dataSnapshot.toString();

                if(dataSnapshot==null)return;
                for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                    userIdList.add(postSnapshot.getKey());
                }


                //cusId = cusId.substring(48,76);
                //cusId = String.valueOf(dataSnapshot.getValue());
                //cusId=(String) dataSnapshot.child("customerRequest").getValue();
                //cusId = dataSnapshot.child("customerRequest").getChildren().toString();

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        DatabaseReference startW = FirebaseDatabase.getInstance().getReference().child("customerRequest").child("wkJqz44AR0QsuxLHaKsbqMNtzIo1").child("confirmed");//customerId
//        DatabaseReference startW = FirebaseDatabase.getInstance().getReference().child("customerRequest").child("MxGNFvftoeRUriFVqIQZ0Kcu8Me2").child("confirmed");//customerId
        startW.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    confirmed = dataSnapshot.getValue().toString();
                    Toast.makeText(getApplicationContext(), "Confirmed by customer", Toast.LENGTH_LONG).show();
                    mStart.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        DatabaseReference fareW = FirebaseDatabase.getInstance().getReference().child("customerRequest").child("wkJqz44AR0QsuxLHaKsbqMNtzIo1").child("fare");//customerId
//        DatabaseReference fareW = FirebaseDatabase.getInstance().getReference().child("customerRequest").child("MxGNFvftoeRUriFVqIQZ0Kcu8Me2").child("fare");//customerId
        fareW.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Fare = dataSnapshot.getValue().toString();
                    Toast.makeText(getApplicationContext(), Fare, Toast.LENGTH_LONG).show();

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });


        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        rootRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.hasChild("customerRequest")){}
                else {
                    if(pickupMarker != null){
                        pickupMarker.remove();
                    }

                    wID.setText("The customer cancelled the service");

                    //



                    AlertDialog.Builder builder1 = new AlertDialog.Builder(WalkerMapActivity.this);
                    builder1.setMessage("The service was cancelled by the customer");
                    builder1.setCancelable(true);

                    builder1.setPositiveButton(
                            "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });
                    builder1.show();
                    //AlertDialog alert11 = builder1.create();
                    //alert11.show();
                    //




                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference refu = FirebaseDatabase.getInstance().getReference("Users/Walkers");
                    GeoFire geoFireu = new GeoFire(refu);
                    geoFireu.removeLocation(userId);

                    DatabaseReference refw = FirebaseDatabase.getInstance().getReference("walkersWorking");
                    GeoFire geoFirew = new GeoFire(refw);
                    geoFirew.removeLocation(userId);

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("walkersAvailable");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });


        //
        //cusId = FirebaseDatabase.getInstance().getReference().child("customerRequest").push().getKey();
        //cusId = userIdList.toString();
        assignedCustomerPickUpLocationRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child("wkJqz44AR0QsuxLHaKsbqMNtzIo1").child("l");//customerId
//        assignedCustomerPickUpLocationRef = FirebaseDatabase.getInstance().getReference().child("customerRequest").child("MxGNFvftoeRUriFVqIQZ0Kcu8Me2").child("l");//customerId

        assignedCustomerPickUpLocationRefListener = assignedCustomerPickUpLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ///
                //wID.setText( "" + FirebaseDatabase.getInstance().getReference().child("customerRequest"));
                //wID.setText( "" + cusId);

                ///
                if(dataSnapshot.exists() && !customerId.equals("")){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    if(map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }

                    if(map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }

                    ///
                    wLoc.setText("customer location: " + locationLat + ", " + locationLng);
                    //wID.setText("Change in customerRequest");
                    ///

                    LatLng walkerLatLng = new LatLng(locationLat, locationLng);
                    pickupMarker = mMap.addMarker(new MarkerOptions().position(walkerLatLng).title("Pick up location"));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
        ///
        // wLoc.setText("after onDataChange from getAssignedCustomerPickUpLocation()");
        ///

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);

    }

    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }


    @Override
    public void onLocationChanged(Location location) {
        if(getApplicationContext() != null){
            mLastLocation = location;
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(16));

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("walkersAvailable");
            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("walkersWorking");

            GeoFire geoFireAvailable = new GeoFire(refAvailable);
            GeoFire geoFireWorking = new GeoFire(refWorking);

            switch (customerId){
                case "":
                    geoFireWorking.removeLocation(userId);
                    geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;

                default:
                    geoFireAvailable.removeLocation(userId);
                    geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;
            }
        }
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000); //Location is updated every second
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(WalkerMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    /*
    private void connectWalker(){
        checkLocationPermission();
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

     */

    private void disconnectWalker(){
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("walkersAvailable");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);

        DatabaseReference refw = FirebaseDatabase.getInstance().getReference("walkersWorking");
        GeoFire geoFirew = new GeoFire(refw);
        geoFirew.removeLocation(userId);

        DatabaseReference refu = FirebaseDatabase.getInstance().getReference("Users/Walkers");
        GeoFire geoFireu = new GeoFire(refu);
        geoFireu.removeLocation(userId);

        if(pickupMarker != null){
            pickupMarker.remove();
        }

    }

    final int LOCATION_REQUEST_CODE = 1;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case LOCATION_REQUEST_CODE:{
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this);
                }else{
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!isLoggingOut){
            disconnectWalker();
        }
    }
}
