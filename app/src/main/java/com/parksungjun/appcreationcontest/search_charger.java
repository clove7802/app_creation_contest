package com.parksungjun.appcreationcontest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;

import org.osmdroid.api.IMapController;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class search_charger extends AppCompatActivity {
    private final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private final String[] locationPermissions = {android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    private GeoPoint startPosition = null;
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
    private Spinner spinnerProvince, spinnerCity, spinnerDistrict;
    private Button btnSearch;

    private Map<String, String[]> cityMap;
    private Map<String, String[]> districtMap;
    private MapView map = null;
    private GeoPoint centerPosition = null;
    private String TAG = "DEBUGAPP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_charger);

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

        map = (MapView) findViewById(R.id.mapView);
        // osmdroid 설정 초기화
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));

        map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        spinnerProvince = findViewById(R.id.spinnerProvince);
        spinnerCity = findViewById(R.id.spinnerCity);
        spinnerDistrict = findViewById(R.id.spinnerDistrict);
        btnSearch = findViewById(R.id.btnSearch);

        // 예시 데이터
        String[] provinces = {"시/도를 선택하세요", "경상남도", "서울특별시", "부산광역시"};

        cityMap = new HashMap<>();
        cityMap.put("경상남도", new String[]{"시/군/구를 선택하세요", "창원시 성산구", "남해군", "진주시"});
        cityMap.put("서울특별시", new String[]{"시/군/구를 선택하세요", "강남구", "서초구", "송파구"});
        cityMap.put("부산광역시", new String[]{"시/군/구를 선택하세요", "해운대구", "수영구", "남구"});

        districtMap = new HashMap<>();
        districtMap.put("창원시 성산구", new String[]{"동/리/읍/면을 선택하세요", "대방동", "상남동", "중앙동"});
        districtMap.put("남해군", new String[]{"동/리/읍/면을 선택하세요", "남해읍", "이동면", "서면"});
        districtMap.put("진주시", new String[]{"동/리/읍/면을 선택하세요", "평거동", "상평동", "칠암동"});
        districtMap.put("강남구", new String[]{"동/리/읍/면을 선택하세요", "역삼동", "삼성동", "논현동"});
        districtMap.put("서초구", new String[]{"동/리/읍/면을 선택하세요", "서초동", "잠원동", "방배동"});
        districtMap.put("송파구", new String[]{"동/리/읍/면을 선택하세요", "잠실동", "문정동", "가락동"});
        districtMap.put("해운대구", new String[]{"동/리/읍/면을 선택하세요", "우동", "중동", "좌동"});
        districtMap.put("수영구", new String[]{"동/리/읍/면을 선택하세요", "광안동", "남천동", "민락동"});
        districtMap.put("남구", new String[]{"동/리/읍/면을 선택하세요", "대연동", "용호동", "문현동"});

        // 스피너에 데이터 추가
        ArrayAdapter<String> provinceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, provinces);
        provinceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvince.setAdapter(provinceAdapter);

        // 처음에는 City와 District 스피너를 비활성화
        spinnerCity.setEnabled(false);
        spinnerDistrict.setEnabled(false);


        // City와 District 스피너를 초기화합니다.
        ArrayAdapter<String> emptyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"시/도를 선택하세요"});
        emptyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(emptyAdapter);
        spinnerDistrict.setAdapter(emptyAdapter);

        spinnerProvince.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedProvince = parent.getSelectedItem().toString();
                if (!selectedProvince.equals("시/도를 선택하세요")) {
                    updateCitySpinner(selectedProvince);
                    spinnerCity.setEnabled(true);  // City 스피너 활성화
                    spinnerDistrict.setEnabled(false); // Province를 선택하면 District 스피너 비활성화
                } else {
                    spinnerCity.setEnabled(false);
                    spinnerDistrict.setEnabled(false);

                }
                spinnerCity.setSelection(0);
                spinnerDistrict.setSelection(0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                spinnerCity.setEnabled(false);
                spinnerDistrict.setEnabled(false);

            }
        });

        spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    spinnerDistrict.setEnabled(false);
                    btnSearch.setEnabled(false); // 버튼 숨기기
                    return;
                }
                String selectedCity = parent.getSelectedItem().toString();
                updateDistrictSpinner(selectedCity);
                spinnerDistrict.setEnabled(true);  // District 스피너 활성화
                btnSearch.setEnabled(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                spinnerDistrict.setEnabled(false);
                btnSearch.setEnabled(false); // 버튼 숨기기
            }
        });

        spinnerDistrict.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    return;
                }
                btnSearch.setVisibility(View.VISIBLE);  // 모든 선택이 완료되면 검색 버튼 보이기
                btnSearch.setEnabled(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnSearch.setOnClickListener(v -> {
            String selectedProvince = spinnerProvince.getSelectedItem().toString();
            String selectedCity = spinnerCity.getSelectedItem().toString();
            String selectedDistrict = spinnerDistrict.getSelectedItem().toString();

            if (!selectedDistrict.equals("동/리/읍/면을 선택하세요")) {
                //전체 주소를 선택했을경우
                String fullAddress = selectedProvince + " " + selectedCity + " " + selectedDistrict;

                map.getOverlays().clear();
                Log.d(TAG,"Address : "+ fullAddress);
                new GeocodeTask().execute(fullAddress);
                new FetchChargersTask().execute(fullAddress);

                btnSearch.setEnabled(false);
            } else {
                //동/리/읍/면을 선택하지 않았을 경우
                String fullAddress = selectedProvince + " " + selectedCity;

                map.getOverlays().clear();
                Log.d(TAG,"Address : "+ fullAddress);
                new GeocodeTask().execute(fullAddress);
                new FetchChargersTask().execute(fullAddress);

                map.setVisibility(View.VISIBLE); // 지도 표시
                btnSearch.setEnabled(false);
            }
        });

        map.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 화면을 터치했을 때
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    // 모든 열려있는 InfoWindow를 닫음
                    closeAllInfoWindowsOn(map);
                    Log.d(TAG, "InfoWindow 닫기");
                }
                return false; // 이벤트를 소비하지 않고 계속 전달
            }
        });

    }

    private void updateCitySpinner(String selectedProvince) {
        String[] cities = cityMap.get(selectedProvince);
        ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cities);
        cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCity.setAdapter(cityAdapter);
        spinnerCity.setSelection(0);  // 기본 선택 항목 없음
    }

    private void updateDistrictSpinner(String selectedCity) {
        String[] districts = districtMap.get(selectedCity);
        ArrayAdapter<String> districtAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, districts);
        districtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDistrict.setAdapter(districtAdapter);
        spinnerDistrict.setSelection(0);  // 기본 선택 항목 없음
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume(); // Needed for osmdroid
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause(); // Needed for osmdroid
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (map != null) {
            map.onDetach(); // Needed for osmdroid
        }
    }
    private class GeocodeTask extends AsyncTask<String, Void, List<Address>> {

        @Override
        protected List<Address> doInBackground(String... addresses) {
            Geocoder geocoder = new Geocoder(search_charger.this, Locale.getDefault());
            try {
                // 주소를 위도와 경도로 변환
                return geocoder.getFromLocationName(addresses[0], 1);
            } catch (IOException e) {
                Log.e(TAG, "Geocoding API call failed", e);
                return null;
            }
        }
        @Override
        protected void onPostExecute(List<Address> addresses) {
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                centerPosition = new GeoPoint(address.getLatitude(), address.getLongitude());
                Log.d(TAG, "검색 Latitude: " + address.getLatitude() + ", Longitude: " + address.getLongitude());
                IMapController mapController = map.getController();

                // 기본 지도 줌 레벨 설정
                mapController.setZoom(15.5);
                // 지도 중심 위치 설정
                mapController.setCenter(centerPosition);
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        runOnUiThread(() -> {
                            map.setVisibility(View.VISIBLE); // 지도 표시
                        });
                    }
                }, 0, 1000);

            } else {
                Log.d(TAG, "Failed to get location for the given address.");
            }
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
                    String urlStr = apiUrl + "serviceKey=" + serviceKey + "&pageNo=1&numOfRows=150&addr=" + address;
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
        protected void onPostExecute(List<search_charger.ChargerInfo> chargerInfoList) {
            if (chargerInfoList != null && !chargerInfoList.isEmpty()) {
                for (search_charger.ChargerInfo chargerInfo : chargerInfoList) {
                    boolean markerExists = false;
                    boolean hasFastCharger = false;
                    boolean hasSlowCharger = false;

                    for (Overlay overlay : map.getOverlays()) {
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
                                    existingMarker.setIcon(ContextCompat.getDrawable(search_charger.this, R.drawable.charger_1));
                                } else if (hasFastCharger) {
                                    existingMarker.setIcon(ContextCompat.getDrawable(search_charger.this, R.drawable.charger_2));
                                }
                                // markers 리스트를 채우는 로직
                                existingMarker.setInfoWindow(new CustomInfoWindow(R.layout.bubble, map, search_charger.this, existingMarker));
                                existingMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            }
                        }
                    }

                    if (!markerExists) {
                        Marker marker = new Marker(map);
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
                            marker.setIcon(ContextCompat.getDrawable(search_charger.this, R.drawable.charger_3));
                        } else if (hasFastCharger) {
                            marker.setIcon(ContextCompat.getDrawable(search_charger.this, R.drawable.charger_2));
                        }

                        marker.setInfoWindow(new CustomInfoWindow(R.layout.bubble, map, search_charger.this, marker));
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                        map.getOverlays().add(marker);
                    }
                }
                map.invalidate();
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


    public class CustomInfoWindow extends InfoWindow {

        private Context context;
        private int layoutResId;
        private Marker mMarker;
        private List<Marker> markers;

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
            Log.d(TAG, "KakaoMap으로 길안내");
            double currentLat = centerPosition.getLatitude();
            double currentLongi = centerPosition.getLongitude();
            double markerLat = markerPosition.getLatitude();
            double markerLongi = markerPosition.getLongitude();


            // Create intent with KakaoMap URI for navigation
            Uri kakaoMapUri = Uri.parse("kakaomap://route?sp=" + currentLat + "," + currentLongi + "&ep=" + markerLat + "," + markerLongi);
            Log.d(TAG, kakaoMapUri.toString());
            Intent kakaoMapIntent = new Intent(Intent.ACTION_VIEW, kakaoMapUri);

            try {
                startActivity(kakaoMapIntent);
            } catch (ActivityNotFoundException e) {
                // Show dialog if no app is available to handle the intent
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(search_charger.this);
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
                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(search_charger.this);
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
    // 즐겨찾기 등록 메서드
    private void addFavorite(String address, String name, Double lat, Double longi) {
        Log.d(TAG, "즐겨찾기 등록: " + address + ", " + name + ", " + lat + ", " + longi);
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
    private String getStatusText(String status) {
        switch (status) {
            case "1":
                return "통신이상";
            case "2":
                return "충전대기";
            case "3":
                return "충전중";
            case "4":
                return "운영중지";
            case "5":
                return "점검중";
            case "9":
                return "상태미확인";
            default:
                return "알 수 없음";
        }
    }
}
