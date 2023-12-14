package org.primeit.compass;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.primeit.compass.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.List;
import android.os.Handler;
import android.os.Looper;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.location.Location;
import android.location.Address;
import android.location.Geocoder;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, SensorEventListener {

    ActivityMainBinding binding;
    float currentDegree = 0f;
    private boolean recording = false;
    private StringBuilder sensorData = new StringBuilder();
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] lastAcceleration = new float[3];
    private long lastTimestamp;
    private double totalDistance = 0;
    private static final float MOVEMENT_THRESHOLD = 0.1f; // Atur nilai sesuai kebutuhan

    private static final int NUM_SAMPLES = 10;
    private float[][] accelerationBuffer = new float[NUM_SAMPLES][3];
    private int bufferIndex = 0;
    private long sensorTimeReference = 0l;
    private long myTimeReference = 0l;

    private TextView accelerometerText;
    private TextView magnetometerText;
    private TextView latitudeText;
    private TextView longitudeText;
    private TextView textViewAddress;
    String magnetometer_x, magnetometer_y, magnetometer_z;
    String acceleromter_x, acceleromter_y, acceleromter_z, acceleromter_m;
    public String StringLatitude, StringLongitude;

    private Handler handler = new Handler(Looper.getMainLooper());

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private FusedLocationProviderClient fusedLocationClient;

    private MapView mapView;
    private GoogleMap googleMap;

    private long lastSensorTimestamp = 0;
    private static final long MIN_SENSOR_INTERVAL_MS = 1000; // 1 detik
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Check and request location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            showToast("Permission is granted.");
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        accelerometerText = findViewById(R.id.accelerometerText);
        magnetometerText = findViewById(R.id.magnetometerText);
        latitudeText = findViewById(R.id.latitudeText);
        longitudeText = findViewById(R.id.longitudeText);
        textViewAddress = findViewById(R.id.textViewAddress);

        Button startButton = findViewById(R.id.startButton);
        Button finishButton = findViewById(R.id.finishButton);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        }
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                startRecording();
            }
        });
        finishButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                finishRecording();
            }
        });
    }

    private void startRecording() {
        recording = true;
        sensorData.setLength(0); // Clear previous data
        sensorData.append("Timestamp;").append("Latitude;Longitude;").append("MagenetometerX;").append("MagenetometerY;").append("MagenetometerZ;")
                .append("AccelerometerX;").append("AccelerometerY;").append("AccelerometerZ;").append("AccelerometerMagnitude;")
                .append("\n");

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        if (accelerometer != null) {
            totalDistance = 0;
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Accelerometer not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void finishRecording() {
        recording = false;
        sensorManager.unregisterListener(this);
        saveDataToFile();
        Toast.makeText(this, "Recording finished", Toast.LENGTH_SHORT).show();
    }

    private void saveDataToFile() {
        String fileName = "sensor_data.txt";
        String data = sensorData.toString();
        Log.d("MainActivity", "Data" + data);
        try {
            File root = new File(getExternalFilesDir(null), "SensorData");
            if (!root.exists()) {
                // Create the directory if it doesn't exist
                if (root.mkdirs()) {
                    Log.d("MainActivity", "Directory created");
                } else {
                    Log.e("MainActivity", "Failed to create directory");
                }
            }

            File file = new File(root, fileName);
            FileWriter writer = new FileWriter(file);
            writer.append(data);
            writer.flush();
            writer.close();

            Toast.makeText(this, "Data saved to " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving data", Toast.LENGTH_SHORT).show();
        }
    }

    private void getLastLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();
                            // Do something with the latitude and longitude
                            StringLatitude = String.valueOf(latitude);
                            StringLongitude = String.valueOf(longitude);
                            // Dapatkan alamat berdasarkan koordinat
                            getAddressFromLatLng(new LatLng(latitude, longitude));
                            updateMap(new LatLng(latitude, longitude));
                        } else {
                            showToast("Last known location is not available.");
                        }
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        showToast("Error getting last location: " + e.getMessage());
                    }
                });
    }

    private void getAddressFromLatLng(LatLng latLng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);

            if (addresses != null && addresses.size() > 0) {
                Address address = addresses.get(0);
                String addressString = address.getAddressLine(0);

                // Tampilkan alamat pada TextView
                textViewAddress.setText(addressString);
            } else {
                Toast.makeText(this, "No address found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateMap(LatLng latLng) {
        if (googleMap != null) {
            googleMap.clear(); // Clear existing markers

            // Add marker at the current location
            googleMap.addMarker(new MarkerOptions().position(latLng).title("Current Location"));

            // Move camera to the current location
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15);
            googleMap.animateCamera(cameraUpdate);
        }
    }

    public void onMapReady(GoogleMap map) {
        googleMap = map;

        // Enable My Location layer if the permission has been granted.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        } else {
            // Show a rationale and request permission.
            showToast("Location permission is required for displaying current location on the map.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                showToast("Location permission denied. Cannot get location.");
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (recording) {
            long currentTimestamp = System.currentTimeMillis();
            if (currentTimestamp - lastSensorTimestamp < MIN_SENSOR_INTERVAL_MS) {
                return; // Lewati pemanggilan jika belum lewat waktu minimum
            }
            String timestamp = getDate(System.currentTimeMillis(), "HH:mm:ss");
            sensorData.append(timestamp).append(";");
            getLastLocation();

            sensorData.append(StringLatitude).append(";").append(StringLongitude).append(";");
            latitudeText.setText("Latitude : " + StringLatitude);
            longitudeText.setText("Longitude : " + StringLongitude);
            if (event.sensor == magnetometer) {
                // Handle magnetometer data
                magnetometer_x = String.format("%.1f",event.values[0]);
                magnetometer_y = String.format("%.1f",event.values[1]);
                magnetometer_z = String.format("%.1f",event.values[2]);
                accelerometerText.setText("Magnetometer: X = " + magnetometer_x +
                        ", Y = " + magnetometer_y + ", Z = " + magnetometer_z);
            }

            if (event.sensor == accelerometer) {
                float accelerationMagnitude = calculateAccelerationMagnitude(event.values[0], event.values[1], event.values[2]);
                // Handle accelerometer data
                acceleromter_x = String.format("%.1f",event.values[0]);
                acceleromter_y = String.format("%.1f",event.values[1]);
                acceleromter_z = String.format("%.1f",event.values[2]);
                acceleromter_m = String.format("%.1f",accelerationMagnitude);
                magnetometerText.setText("Accelerometer: X = " + acceleromter_x +
                        ", Y = " + acceleromter_y + ", Z = " + acceleromter_z +
                        ", Magnitude = " + acceleromter_m);
            }
            sensorData.append(magnetometer_x).append(";");
            sensorData.append(magnetometer_y).append(";");
            sensorData.append(magnetometer_z).append(";");
            sensorData.append(acceleromter_x).append(";");
            sensorData.append(acceleromter_y).append(";");
            sensorData.append(acceleromter_z).append(";");
            sensorData.append(acceleromter_m).append(";");
            sensorData.append("\n");

            lastSensorTimestamp = currentTimestamp;
        }
    }

    private float calculateAccelerationMagnitude(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public static String getDate(long milliSeconds, String dateFormat)
    {
        // Create a DateFormatter object for displaying date in specified format.
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);

        // Create a calendar object that will convert the date and time value in milliseconds to date.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(milliSeconds);
        return formatter.format(calendar.getTime());
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recording) {
            mapView.onPause();
            finishRecording();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.top_menu, menu);
        return true;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}