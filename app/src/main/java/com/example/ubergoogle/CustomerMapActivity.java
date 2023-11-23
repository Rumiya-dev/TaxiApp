package com.example.ubergoogle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.example.ubergoogle.databinding.ActivityDriverMapBinding;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    private ActivityDriverMapBinding binding;
    private Button mLogout, mRequest;
    private LatLng pickupLocation;//объект который используется для работы широты и долготы пользователя напр. для устновки маркера в этом месте
    private Boolean requestBol = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        //binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        //setContentView(binding.getRoot());

        setContentView(R.layout.activity_costumer_map);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mLogout = (Button) findViewById(R.id.logout);
        mRequest = (Button) findViewById(R.id.request);
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(requestBol){//if customer cancel request to call taxi
                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
                    driverRef.setValue(true);//удаляет id водителя из firebase
                    driverFoundID = null;
                    requestBol = false;
                    geoQuery.removeAllListeners();
                    driverLocationRef.removeEventListener(driverLocationRefListener);

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");

                    if(driverFound != null){

                    }
                    driverFound = false;
                    radius = 1;

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.removeLocation(userId);

                }else {//if customer call taxi
                    requestBol = true;
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("customerRequest");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(),mLastLocation.getLongitude()));
                    pickupLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    mMap.addMarker(new MarkerOptions().position(pickupLocation).title("pickup here"));

                    mRequest.setText("Getting your driver...");

                    getClosestDriver();
                }

            }
        });
    }


    private int radius = 1;
    private Boolean driverFound = false;
    private String driverFoundID;

    GeoQuery geoQuery;
    private void getClosestDriver(){
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driversAvailable");

        GeoFire geoFire = new GeoFire(driverLocation);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);//запрос geofire чтобы получить центирование точки используя объект Latlng для запроса координат

        geoQuery.removeAllListeners();//используется для очистки предыдущих слушателей и предотвращения конфликтов при обновлении запроса GeoFire.


        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {//Вызывается, когда новый ключ (идентификатор) входит в область запроса
                // с использованием GeoFire.
                // В этом методе вы можете обработать событие входа объекта в зону, например, отобразить маркер на карте или выполнить другие действия.

                if(!driverFound && requestBol){
        /*используется для обеспечения того, что код внутри блока выполняется только в том случае, если до этого момента водитель еще не был найден
           /*предотвращает бесконечное увеличение радиуса, так как после увеличения радиуса и
           повторного вызова getClosestDriver, флаг driverFound уже установлен, и новый водитель будет найден только при следующем входе в зону.
            */
                    driverFound = true;
                    driverFoundID = key;

                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();//Получает идентификатор текущего пользователя (клиента), который можно использовать для связи с id водителя
                    HashMap map = new HashMap();
                    map.put("customerRideId", customerId);
                    driverRef.updateChildren(map);//добавление в firebase в к id водителя id клиента папке Drivers

                    getDriverLocation();
                    mRequest.setText("Looking for driver location...");
                }

            }

            @Override
            public void onKeyExited(String key) {//Вызывается, когда объект покидает область запроса.

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {//Вызывается, когда объект внутри области запроса перемещается

            }

            @Override
            public void onGeoQueryReady() {//Вызывается, когда инициированный запрос к геоиндексу завершен
                if(!driverFound){
                    radius++;
                    getClosestDriver();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {//Вызывается, когда возникает ошибка при выполнении запроса к геоиндексу

            }
        });
    }

    private Marker mDriverMarker;
    DatabaseReference driverLocationRef;
    private ValueEventListener  driverLocationRefListener;
    private void getDriverLocation(){//Обновление местоположения водителя в реальном времени
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("driversWorking").child(driverFoundID).child("l");//изменяем название узла на driversWorking
        driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {//every time when location is changed this func will be called
               if(snapshot.exists() && requestBol) {
                   List<Object> map = (List<Object>) snapshot.getValue();//put everything data which we have in snapshot and put it to the List
                   double locationLng = 0;
                   double locationLat = 0;
                   mRequest.setText("Driver found");
                   if(map.get(0) != null){
                      locationLat  = Double.parseDouble(map.get(0).toString());
                   }

                   if(map.get(1) != null){
                       locationLng  = Double.parseDouble(map.get(1).toString());
                   }

                   LatLng driverLatLng = new LatLng(locationLat, locationLng);
                   if(mDriverMarker != null){
                       mDriverMarker.remove();//Удаление старого маркера перед созданием нового
                       // обновляет его положение на карте, предотвращая накопление маркеров в одном месте при перемещении водителя и
                       // изменении его местоположения
                   }
                   Location loc1 = new Location("");//customer location
                   loc1.setLatitude(pickupLocation.latitude);
                   loc1.setLongitude(pickupLocation.longitude);

                   Location loc2 = new Location("");//driver location
                   loc2.setLatitude(driverLatLng.latitude);
                   loc2.setLongitude(driverLatLng.longitude);

                   float distance = loc1.distanceTo(loc2);//distance between driver and customer

                   if(distance < 100){
                       mRequest.setText("Driver is here");
                   }else {
                       mRequest.setText("Driver found" + String.valueOf(distance));
                   }

                   mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("your driver"));
               }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });

    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        mMap.setMyLocationEnabled(true);

        buildGoogleApiClient();
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
    public void onLocationChanged(@NonNull Location location) {
        mLastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11));



    }



    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onStop() {//when user close app, minimize etc.
        super.onStop();



    }
}