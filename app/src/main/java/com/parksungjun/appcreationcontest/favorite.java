package com.parksungjun.appcreationcontest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.osmdroid.util.GeoPoint;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class favorite extends AppCompatActivity {

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
    private ListView listView;
    private List<FavoriteData> favoriteDataList;
    private HashMap<String, FavoriteData> favoriteDataMap = new HashMap<>();
    private TextView favoriteTitleTextView;
    public FavoriteListAdapter favoriteListAdapter;
    private String TAG = "DEBUGAPP";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite);

        // SQLite 데이터베이스 초기화
        dbHelper = new SQLiteOpenHelper(this, DATABASE_NAME, null, DATABASE_VERSION) {
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                        COLUMN_ADDR + " TEXT, " +
                        COLUMN_CSNM + " TEXT);");
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
                onCreate(db);
            }
        };

        // 데이터베이스 열기
        database = dbHelper.getWritableDatabase();

        listView = findViewById(R.id.list_view);
        favoriteDataList = new ArrayList<>();
        favoriteTitleTextView = findViewById(R.id.favlist);

        favoriteListAdapter = new FavoriteListAdapter();
        listView.setAdapter(favoriteListAdapter);

        // 등록된 데이터 로드
        loadFavorites();


        if (ContextCompat.checkSelfPermission(this, locationPermissions[0]) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, locationPermissions[1]) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
            getCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(this, locationPermissions, LOCATION_PERMISSION_REQUEST_CODE);
        }
        //startPosition 초기화
        startPosition = new GeoPoint(0.0, 0.0);

        // favorite list view update when interval time
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    favoriteListAdapter.notifyDataSetChanged();
                });
            }
        }, 0, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 데이터베이스를 닫음
        if (database != null) {
            database.close();
        }
    }

    // 등록된 데이터를 로드하여 리스트에 저장하는 메서드
    private void loadFavorites() {
        // 기존 데이터를 비웁니다
        favoriteDataList.clear();
        favoriteDataMap.clear();

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

                FavoriteData data = new FavoriteData();
                data.Address = address;
                data.Name = name;
                data.Latitude = lat;
                data.Longitude = longi;

                favoriteDataList.add(data);
                favoriteDataMap.put(address, data);
            } while (cursor.moveToNext());
            cursor.close(); // 커서를 사용한 후에는 꼭 닫아주어야 함

            // favlist TextView에 데이터 갯수 표시
            favoriteTitleTextView.setText("등록된 충전소 개수: " + favoriteDataList.size());
        }
        // 어댑터에 데이터 변경을 알립니다
        favoriteListAdapter.notifyDataSetChanged();

        // run fetch chargers task
        for (FavoriteData data : favoriteDataList) {
            FetchChargersTask task = new FetchChargersTask();
            task.execute(data.Address);
        }
    }

    /**
     * 리스트뷰에 표시할 어댑터 클래스
     */
    private class FavoriteListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return favoriteDataList.size();
        }

        @Override
        public Object getItem(int position) {
            return favoriteDataList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.favorite_list_item, parent, false);
            }

            // 리스트뷰 항목 클릭 시 대화상자 표시
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 클릭된 항목의 데이터를 가져옵니다.
                    FavoriteData clickedData = favoriteDataList.get(position);

                    // 대화상자를 생성합니다.
                    final String[] navigationOptions = {"카카오맵으로 길안내", "티맵으로 길안내", "즐겨찾기 삭제"};
                    AlertDialog.Builder builder = new AlertDialog.Builder(favorite.this);
                    builder.setTitle("옵션");
                    //builder.setMessage(clickedData.Name + "\n" + clickedData.Address + "\n위도: " + clickedData.Latitude + "\n경도: " + clickedData.Longitude);
                    builder.setItems(navigationOptions, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case 0:
                                    // Open KakaoMap with navigation
                                    openKakaoMapNavigation(clickedData.Latitude, clickedData.Longitude);
                                    break;
                                case 1:
                                    // Open TMap with navigation
                                    openTMapNavigation(clickedData.Latitude, clickedData.Longitude);
                                    break;
                                case 2:
                                    // 즐겨찾기 삭제
                                    deleteFavorite(clickedData.Address);
                                    break;
                            }
                        }

                        // Method to open KakaoMap for navigation
                        private void openKakaoMapNavigation(double lat, double longi) {
                            double currentLat = startPosition.getLatitude();
                            double currentLongi = startPosition.getLongitude();

                            // Create intent with KakaoMap URI for navigation
                            Uri kakaoMapUri = Uri.parse("kakaomap://route?sp=" + currentLat + "," + currentLongi + "&ep=" + lat + "," + longi);
                            Log.d(TAG, kakaoMapUri.toString());
                            Intent kakaoMapIntent = new Intent(Intent.ACTION_VIEW, kakaoMapUri);
                            try {
                                startActivity(kakaoMapIntent);
                            } catch (ActivityNotFoundException e) {
                                // Show dialog if no app is available to handle the intent
                                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(favorite.this);
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
                        private void openTMapNavigation(double lat, double longi) {
                            // Implement TMap navigation here
                            String chargername = clickedData.Name;

                            // Create intent with TMap URI for navigation
                            Uri TMapUri = Uri.parse("tmap://route?rGoName=" + chargername + "&goalx=" + longi + "&goaly=" + lat);
                            Log.d(TAG, TMapUri.toString());
                            Intent TMapIntent = new Intent(Intent.ACTION_VIEW, TMapUri);

                            try {
                                startActivity(TMapIntent);
                            } catch (ActivityNotFoundException e) {
                                // Show dialog if no app is available to handle the intent
                                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(favorite.this);
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


                    });

                    // 확인 버튼을 추가합니다.
                    builder.setPositiveButton("닫기", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // 사용자가 확인 버튼을 클릭하면 아무 작업도 수행하지 않고 대화상자를 닫습니다.
                        }
                    });

                    // 대화상자를 생성하고 표시합니다.
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            });

            FavoriteData favoriteData = favoriteDataList.get(position);

            TextView textView = convertView.findViewById(R.id.title);
            textView.setText(favoriteData.Name);

            if (favoriteData.IsFetched)
            {
                // add detail info to text description
                StringBuilder detailInfo = new StringBuilder();
                if (favoriteData.ChargerInfos != null)
                {
                    for (ChargerInfo info : favoriteData.ChargerInfos)
                    {
                        detailInfo.append(info.getName() + " : " + getStatusText(info) + "\n");
                    }
                }

                // add description text view
                TextView descriptionTextView = convertView.findViewById(R.id.description);
                descriptionTextView.setText(detailInfo.toString());
            } else {
                // add description text view
                TextView descriptionTextView = convertView.findViewById(R.id.description);
                descriptionTextView.setText("충전소 정보를 가져오는 중입니다.");
            }

            return convertView;
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
                    String urlStr = apiUrl + "serviceKey=" + serviceKey + "&pageNo=1&numOfRows=100&addr=" + address;

                    Log.d(TAG, "API 호출 주소: " + urlStr);

                    URL url = new URL(urlStr);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000); // 10초 후에 연결 시간 초과
                    connection.setReadTimeout(10000); // 10초 후에 읽기 시간 초과
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "HTTP 응답 코드: " + responseCode);

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

                        // 즐겨찾기 데이터에 충전소 정보 저장
                        FavoriteData favoriteData = favoriteDataMap.get(address);
                        if (favoriteData != null)
                        {
                            favoriteData.ChargerInfos = chargerInfoList;
                            favoriteData.IsFetched = true;
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
                StringBuilder result = new StringBuilder();
                for (ChargerInfo chargerInfo : chargerInfoList) {
                    // 여기서 충전소 정보를 이용하여 필요한 작업을 수행하세요.
                    // 예를 들어, 해당 충전소를 지도에 표시하거나 다른 작업을 수행할 수 있습니다.
                    result.append("충전소 이름: ").append(chargerInfo.getName()).append(", 주소: ").append(chargerInfo.getAddress()).append("\n");
                }
                //Toast.makeText(favorite.this, result.toString(), Toast.LENGTH_LONG).show();
            } else {
                Log.d(TAG, "No charger information received");
                Toast.makeText(favorite.this, "충전소 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);

        Log.d(TAG, "Requesting location updates");
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            if (locationResult != null && locationResult.getLastLocation() != null) {
                Location location = locationResult.getLastLocation();
                startPosition = new GeoPoint(location.getLatitude(), location.getLongitude());
                Log.d(TAG, "Location received: " + startPosition.getLatitude() + ", " + startPosition.getLongitude());
            } else {
                Log.d(TAG, "Location result is null or last location is null");
            }
        }
    };

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
    private void showPermissionDeniedDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
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
    private void deleteFavorite(String address) {
        database.delete(TABLE_NAME, COLUMN_ADDR + "=?", new String[]{address});
        loadFavorites(); // 삭제 후 리스트 갱신
    }
    public String getStatusText(ChargerInfo info) {
        switch (info.getStatus()) {
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
