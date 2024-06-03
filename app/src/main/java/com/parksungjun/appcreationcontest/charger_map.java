package com.parksungjun.appcreationcontest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class charger_map extends AppCompatActivity {
    private final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private final String[] locationPermissions = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    private GeoPoint startPosition = null;
    private MapView mapView;
    private Marker currentLocationMarker;
    private int updatemap = 0;
    private final String TAG = "DEBUGAPP";
    // SQLite 관련 변수
    private static final String DATABASE_NAME = "FavoriteChargers.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "favorite_chargers";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_ADDR = "addr";
    private static final String COLUMN_CSNM = "csnm";
    private static final String COLUMN_LAT = "lat";
    private static final String COLUMN_LONGI = "longi";

    private SQLiteOpenHelper dbHelper;
    private SQLiteDatabase database;


    public class CustomInfoWindow extends InfoWindow {

        private Context context;
        private int layoutResId;
        private Marker mMarker;

        public CustomInfoWindow(int layoutResId, MapView mapView, Context context, Marker marker) {
            super(layoutResId, mapView);
            this.context = context;
            this.layoutResId = layoutResId;
            this.mMarker = marker;
        }

        @Override
        public void onOpen(Object item) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(layoutResId, null);

            TextView titleTextView = view.findViewById(R.id.text_title);
            TextView snippetTextView = view.findViewById(R.id.text_snippet);
            TextView subDescriptionTextView = view.findViewById(R.id.text_subdescription);
            // 배경을 둥근 모양으로 설정합니다.
            Drawable background = context.getResources().getDrawable(R.drawable.bubble_background);
            view.setBackground(background);

            titleTextView.setText(mMarker.getTitle());
            snippetTextView.setText(mMarker.getSnippet());
            subDescriptionTextView.setText(mMarker.getSubDescription());

            // Handle click on InfoWindow
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Show dialog for navigation options
                    final String[] navigationOptions = {"카카오맵으로 길안내", "티맵으로 길안내", "즐겨찾기 등록"};
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("옵션을 선택하세요")
                            .setItems(navigationOptions, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    switch (which) {
                                        case 0:
                                            // Open KakaoMap with navigation
                                            openKakaoMapNavigation(mMarker.getPosition());
                                            break;
                                        case 1:
                                            // Open TMap with navigation
                                            openTMapNavigation(mMarker.getPosition());
                                            break;
                                        case 2:
                                            // 즐겨찾기 등록
                                            addFavorite(mMarker.getSnippet(), mMarker.getTitle(), mMarker.getPosition().getLatitude(), mMarker.getPosition().getLongitude());
                                            break;
                                    }
                                }
                            })
                            // 확인 버튼을 추가합니다.
                            .setPositiveButton("닫기", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // 사용자가 확인 버튼을 클릭하면 아무 작업도 수행하지 않고 대화상자를 닫습니다.
                                }
                            })
                            .show();
                }
            });


            mView = view;
        }

        @Override
        public void onClose() {
            // Nothing to do here
        }

        // Method to open KakaoMap for navigation
        private void openKakaoMapNavigation(GeoPoint markerPosition) {
            double currentLat = startPosition.getLatitude();
            double currentLongi = startPosition.getLongitude();
            double markerLat = markerPosition.getLatitude();
            double markerLongi = markerPosition.getLongitude();


            // Create intent with KakaoMap URI for navigation
            Uri kakaoMapUri = Uri.parse("kakaomap://route?sp=" + currentLat + "," + currentLongi + "&ep=" + markerLat + "," + markerLongi);
            Intent kakaoMapIntent = new Intent(Intent.ACTION_VIEW, kakaoMapUri);

            try {
                startActivity(kakaoMapIntent);
            } catch (ActivityNotFoundException e) {
                // Show dialog if no app is available to handle the intent
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(charger_map.this);
                builder.setTitle("앱이 설치되어 있지 않음")
                        .setMessage("카카오맵이 설치되어 있지 않습니다. 카카오맵을 설치하거나 다른 네비게이션 앱을 선택해주세요.")
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Close the dialog
                                dialog.dismiss();
                            }
                        })
                        .show();
            }
        }

        // 티맵 네비게이션을 호출하는 메서드
        private void openTMapNavigation(GeoPoint markerPosition) {
            // Implement TMap navigation here
            String markerName = mMarker.getTitle();
            double markerLat = markerPosition.getLatitude();
            double markerLongi = markerPosition.getLongitude();

            // Create intent with TMap URI for navigation
            Uri TMapUri = Uri.parse("tmap://route?rGoName=" + markerName + "&goalx=" + markerLongi + "&goaly=" + markerLat);
            Log.d(TAG, TMapUri.toString());
            Intent TMapIntent = new Intent(Intent.ACTION_VIEW, TMapUri);

            // Verify that the intent can be resolved
            try {
                startActivity(TMapIntent);
            } catch (ActivityNotFoundException e) {
                // Show dialog if no app is available to handle the intent
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(charger_map.this);
                builder.setTitle("앱이 설치되어 있지 않음")
                        .setMessage("TMap이 설치되어 있지 않습니다. TMap을 설치하거나 다른 네비게이션 앱을 선택해주세요.")
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Close the dialog
                                dialog.dismiss();
                            }
                        })
                        .show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // SQLite 데이터베이스 초기화
        dbHelper = new SQLiteOpenHelper(this, DATABASE_NAME, null, DATABASE_VERSION) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_ADDR + " TEXT, " +
                COLUMN_CSNM + " TEXT, " +
                COLUMN_LAT + " DOUBLE, " +
                COLUMN_LONGI + " DOUBLE);" );
                }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
                onCreate(db);
            }
        };
        database = dbHelper.getWritableDatabase();

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));

        mapView = findViewById(R.id.now_map);

        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);


        if (ContextCompat.checkSelfPermission(this, locationPermissions[0]) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, locationPermissions[1]) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
            getCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(this, locationPermissions, LOCATION_PERMISSION_REQUEST_CODE);
        }

        mapView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 화면을 터치했을 때
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    // 모든 열려있는 InfoWindow를 닫음
                    closeAllInfoWindowsOn(mapView);
                    Log.d(TAG, "InfoWindow 닫기");
                }
                return false; // 이벤트를 소비하지 않고 계속 전달
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);

        Log.d(TAG, "Requesting location updates");
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            if (locationResult != null && locationResult.getLastLocation() != null) {
                Location location = locationResult.getLastLocation();
                GeoPoint currentPosition = new GeoPoint(location.getLatitude(), location.getLongitude());
                Log.d(TAG, "Location received: " + currentPosition);
                startPosition = currentPosition;
                updateMapWithCurrentLocation(currentPosition);
            } else {
                Log.d(TAG, "Location result is null or last location is null");
            }
        }
    };

    private void updateMapWithCurrentLocation(GeoPoint currentPosition) {
        if (startPosition != null) {
            Log.d(TAG, "Updating map with current location: " + startPosition);
            if (updatemap == 0) {
                updatemap++;
                mapView.getController().setZoom(17.5);
                mapView.getController().setCenter(startPosition);
                mapView.setVisibility(View.VISIBLE);
                fetchNearbyChargers(currentPosition);

                if (currentLocationMarker == null) {
                    currentLocationMarker = new Marker(mapView);
                    currentLocationMarker.setPosition(startPosition);
                    currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    currentLocationMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.blue_marker));
                    currentLocationMarker.setOnMarkerClickListener((marker, mapView) -> true);
                    mapView.getOverlays().add(currentLocationMarker);
                }

            } else {
                currentLocationMarker.setPosition(currentPosition);
            }
            mapView.invalidate();
            showAddress(currentPosition);
        } else {
            Log.d(TAG, "startPosition is null");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Location permission granted");
                getCurrentLocation();
            } else {
                Log.d(TAG, "Location permission denied");
                showPermissionDeniedDialog();
            }
        }
    }

    private void showAddress(GeoPoint location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder fullAddress = new StringBuilder();
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    String line = address.getAddressLine(i);
                    line = line.replace("대한민국", "");
                    fullAddress.append(line).append(" ");
                }
                String addressText = fullAddress.toString().trim();
                Log.d(TAG, "Current Address: " + addressText);
            } else {
                Toast.makeText(this, "Unable to fetch address", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Unable to fetch address");
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Geocoder service not available", Toast.LENGTH_LONG).show();
            Log.d(TAG, "Geocoder service not available", e);
        }
    }

    private void showPermissionDeniedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Location Permission Denied")
                .setMessage("Location permission is required to use this feature. Please enable it in the app settings.")
                .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            e.printStackTrace();
                            Log.d(TAG, "Activity not found for settings intent", e);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void fetchNearbyChargers(GeoPoint currentPosition) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(currentPosition.getLatitude(), currentPosition.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String addr = address.getAdminArea() + " " + address.getLocality();
                if (address.getSubLocality() != null) {
                    addr += " " + address.getSubLocality();
                } else if (address.getThoroughfare() != null) {
                    addr += " " + address.getThoroughfare();
                }
                new FetchChargersTask().execute(addr);
            } else {
                Toast.makeText(this, "Unable to fetch address for chargers", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Unable to fetch address for chargers");
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Geocoder service not available", Toast.LENGTH_LONG).show();
            Log.d(TAG, "Geocoder service not available", e);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class FetchChargersTask extends AsyncTask<String, Void, List<ChargerInfo>> {

        @Override
        protected List<ChargerInfo> doInBackground(String... addresses) {
            String address = addresses[0];
            List<ChargerInfo> chargerInfoList = new ArrayList<>();
            int retries = 3;
            int attempt = 0;

            while (attempt < retries) {
                attempt++;
                try {
                    String apiUrl = "http://openapi.kepco.co.kr/service/EvInfoServiceV2/getEvSearchList?";
                    String serviceKey = "공공데이터 서비스키";
                    String urlStr = apiUrl + "serviceKey=" + serviceKey + "&pageNo=1&numOfRows=30&addr=" + address;
                    Log.d(TAG, "API 호출 주소: " + urlStr);
                    URL url = new URL(urlStr);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000); // 10초 후에 연결 시간 초과
                    connection.setReadTimeout(10000); // 10초 후에 읽기 시간 초과
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = connection.getInputStream();
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document document = builder.parse(inputStream);
                        document.getDocumentElement().normalize();
                        NodeList nodeList = document.getElementsByTagName("item");

                        for (int i = 0; i < nodeList.getLength(); i++) {
                            Element element = (Element) nodeList.item(i);
                            ChargerInfo chargerInfo = new ChargerInfo();

                            // Parse and set
                            chargerInfo.setPointName(getElementValue(element, "csNm"));
                            chargerInfo.setAddress(getElementValue(element, "addr"));
                            chargerInfo.setLatitude(Double.parseDouble(getElementValue(element, "lat")));
                            chargerInfo.setLongitude(Double.parseDouble(getElementValue(element, "longi")));
                            chargerInfo.setStatus(getElementValue(element, "cpStat"));
                            chargerInfo.setName(getElementValue(element, "cpNm"));
                            chargerInfo.setChargeType(getElementValue(element, "chargeTp"));

                            chargerInfoList.add(chargerInfo);
                        }

                        return chargerInfoList; // 성공 시 바로 반환
                    } else {
                        Log.d(TAG, "HTTP request failed with response code " + responseCode);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(TAG, "Exception during HTTP request on attempt " + attempt, e);
                }

                // 재시도 전 대기
                try {
                    Thread.sleep(2000); // 2초 대기
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return chargerInfoList;
        }

        private String getElementValue(Element parent, String tagName) {
            NodeList nodeList = parent.getElementsByTagName(tagName);
            String value = null;
            if (nodeList.getLength() > 0) {
                value = nodeList.item(0).getTextContent();
                //Log.d(TAG, "Element with tag name " + tagName + " Found in XML");
            } else {
                Log.d(TAG, "Element with tag name " + tagName + " not found in XML");
            }
            return value;
        }

        @Override
        protected void onPostExecute(List<ChargerInfo> chargerInfoList) {
            if (chargerInfoList != null && !chargerInfoList.isEmpty()) {
                for (ChargerInfo chargerInfo : chargerInfoList) {
                    boolean markerExists = false;
                    boolean hasFastCharger = false;
                    boolean hasSlowCharger = false;

                    for (Overlay overlay : mapView.getOverlays()) {
                        if (overlay instanceof Marker) {
                            Marker existingMarker = (Marker) overlay;
                            GeoPoint existingPosition = existingMarker.getPosition();
                            if (existingPosition.getLatitude() == chargerInfo.getLatitude() && existingPosition.getLongitude() == chargerInfo.getLongitude()) {
                                markerExists = true;
                                existingMarker.setTitle(chargerInfo.getPointName());
                                existingMarker.setSnippet(chargerInfo.getAddress());

                                String statusText = getStatusText(chargerInfo.getStatus());
                                String subDescription = chargerInfo.getName() + " : " + statusText;
                                if (existingMarker.getSubDescription() != null && !existingMarker.getSubDescription().isEmpty()) {
                                    subDescription = existingMarker.getSubDescription() + "\n" + subDescription;
                                }
                                existingMarker.setSubDescription(subDescription);

                                if ("2".equals(chargerInfo.getChargeType())) {
                                    hasFastCharger = true;
                                }
                                if ("1".equals(chargerInfo.getChargeType())) {
                                    hasSlowCharger = true;
                                }

                                if (hasSlowCharger) {
                                    existingMarker.setIcon(ContextCompat.getDrawable(charger_map.this, R.drawable.charger_1));
                                } else if (hasFastCharger) {
                                    existingMarker.setIcon(ContextCompat.getDrawable(charger_map.this, R.drawable.charger_2));
                                }
                                // markers 리스트를 채우는 로직
                                existingMarker.setInfoWindow(new CustomInfoWindow(R.layout.bubble, mapView, charger_map.this, existingMarker));
                                existingMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            }
                        }
                    }

                    if (!markerExists) {
                        Marker marker = new Marker(mapView);
                        GeoPoint position = new GeoPoint(chargerInfo.getLatitude(), chargerInfo.getLongitude());
                        marker.setPosition(position);
                        marker.setTitle(chargerInfo.getPointName());
                        marker.setSnippet(chargerInfo.getAddress());

                        String statusText = getStatusText(chargerInfo.getStatus());
                        String subDescription = chargerInfo.getName() + " : " + statusText;
                        marker.setSubDescription(subDescription);

                        if ("2".equals(chargerInfo.getChargeType())) {
                            hasFastCharger = true;
                        }
                        if ("1".equals(chargerInfo.getChargeType())) {
                            hasSlowCharger = true;
                        }

                        if (hasSlowCharger) {
                            marker.setIcon(ContextCompat.getDrawable(charger_map.this, R.drawable.charger_3));
                        } else if (hasFastCharger) {
                            marker.setIcon(ContextCompat.getDrawable(charger_map.this, R.drawable.charger_2));
                        }

                        marker.setInfoWindow(new CustomInfoWindow(R.layout.bubble, mapView, charger_map.this, marker));
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                        mapView.getOverlays().add(marker);
                    }
                }
                mapView.invalidate();
            } else {
                Log.d(TAG, "No charger information received");
            }
        }


        private String getStatusText(String status) {
            switch (status) {
                case "1":
                    return "충전가능";
                case "2":
                    return "충전중";
                case "3":
                    return "고장/점검";
                case "4":
                    return "통신이상";
                case "9":
                    return "충전예약";
                default:
                    return "상태 확인 불가";
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
    private static class ChargerInfo {
        private String pointName;
        private String address;
        private double latitude;
        private double longitude;
        private String status;
        private String name;
        private String chargeType;

        public String getPointName() {
            return pointName;
        }

        public void setPointName(String pointName) {
            this.pointName = pointName;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getChargeType() {
            return chargeType;
        }

        public void setChargeType(String chargeType) {
            this.chargeType = chargeType;
        }
    }

    // 즐겨찾기 등록 메서드
    private void addFavorite(String address, String name, Double lat, Double longi) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_ADDR, address);
        values.put(COLUMN_CSNM, name);
        values.put(COLUMN_LAT, lat);
        values.put(COLUMN_LONGI, longi);

        long newRowId = database.insert(TABLE_NAME, null, values);
        if (newRowId != -1) {
            Toast.makeText(this, "즐겨찾기에 등록되었습니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "등록에 실패했습니다.", Toast.LENGTH_SHORT).show();
        }
    }
    // 등록된 데이터를 로드하여 토스트 메시지로 출력하는 메서드
    private void loadFavorites() {
        Cursor cursor = database.query(
                TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                int columnNameIndex = cursor.getColumnIndex(COLUMN_CSNM);
                int columnAddrIndex = cursor.getColumnIndex(COLUMN_ADDR);
                int columnLatIndex = cursor.getColumnIndex(COLUMN_LAT);
                int columnLongiIndex = cursor.getColumnIndex(COLUMN_LONGI);

                if (columnAddrIndex < 0 || columnNameIndex < 0 || columnLatIndex < 0 || columnLongiIndex < 0)
                {
                    Log.d(TAG, "Column index not found");
                    continue;
                }

                String address = cursor.getString(columnAddrIndex);
                String name = cursor.getString(columnNameIndex);
                double lat = cursor.getDouble(columnLatIndex);
                double longi = cursor.getDouble(columnLongiIndex);

                // 여기에서 address를 이용하여 마커를 추가하거나 지도에 표시할 수 있음
                Log.d (TAG, "즐겨찾기 데이터: " + address + " / " + name + " / " + lat + " / " + longi);
            } while (cursor.moveToNext());
        }
    }
    public static void closeAllInfoWindowsOn(MapView map) {
        if (map != null) {
            for (Overlay overlay : map.getOverlays()) {
                if (overlay instanceof Marker) {
                    Marker marker = (Marker) overlay;
                    if (marker.isInfoWindowShown()) {
                        marker.closeInfoWindow();
                    }
                }
            }
        }
    }
}
