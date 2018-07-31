package com.meekmika.warsart.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.SphericalUtil;
import com.meekmika.warsart.R;
import com.meekmika.warsart.adapters.ImagePagerAdapter;
import com.meekmika.warsart.data.model.StreetArt;
import com.meekmika.warsart.utils.GeoUtil;
import com.rd.PageIndicatorView;

import org.w3c.dom.Text;

import timber.log.Timber;

import static android.content.Context.LOCATION_SERVICE;
import static com.meekmika.warsart.ui.DetailActivity.STREET_ART;

public class DetailFragment extends Fragment implements OnMapReadyCallback {

    private static final int PERMISSION_ACCESS_FINE_LOCATION_REQUEST_CODE = 818;
    private static final float ZOOM_LEVEL = 14f;
    private static final long LOCATION_REFRESH_TIME = 1200000; //20 min
    private static final float LOCATION_REFRESH_DISTANCE = 100; //100 meters
    private StreetArt streetArt;
    private GoogleMap googleMap;
    private LocationManager locationManager;
    private TextView distanceTextView;

    public static DetailFragment newInstance(StreetArt streetArt) {

        Bundle args = new Bundle();
        args.putParcelable(STREET_ART, streetArt);

        DetailFragment fragment = new DetailFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        streetArt = args.getParcelable(STREET_ART);

        SupportMapFragment mapFragment = new SupportMapFragment();
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.map_container, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_detail, container, false);

        ViewPager viewPager = rootView.findViewById(R.id.image_view_pager);
        FrameLayout pageIndicatorContainer = rootView.findViewById(R.id.page_indicator_container);
        TextView titleTextView = rootView.findViewById(R.id.tv_street_art_title);
        TextView addressTextView = rootView.findViewById(R.id.tv_street_art_address);
        TextView artistTextView = rootView.findViewById(R.id.tv_street_art_artist);
        TextView descriptionTextView = rootView.findViewById(R.id.tv_street_art_description);
        distanceTextView = rootView.findViewById(R.id.tv_street_art_distance);
        FixedAspectRatioFrameLayout mapContainer = rootView.findViewById(R.id.map_container);

        if (streetArt != null) {
            viewPager.setAdapter(new ImagePagerAdapter(streetArt.getImages()));
            titleTextView.setText(streetArt.getTitle());
            addressTextView.setText(streetArt.getAddress());
            artistTextView.setText(streetArt.getArtist());
            descriptionTextView.setText(streetArt.getDescription());
            if (streetArt.getImages().size() < 2) {
                pageIndicatorContainer.setVisibility(View.GONE);
            }
            mapContainer.setAspectRatio(9/16f);
        }

        ImageButton openMapsButton = rootView.findViewById(R.id.btn_open_maps);
        openMapsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Uri location = Uri.parse("geo:0,0?q=" + streetArt.getAddress());
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW,
                        location);
                startActivity(intent);
            }
        });
        return rootView;
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        setupPermissions();
        LatLng position = GeoUtil.getCoordinates(getContext(), streetArt.getAddress());
        if (position != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(position));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, ZOOM_LEVEL));
            googleMap.addMarker(new MarkerOptions().position(position));
        }
    }

    private void setupPermissions() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String[] locationPermission = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
                requestPermissions(locationPermission, PERMISSION_ACCESS_FINE_LOCATION_REQUEST_CODE);
            }
        } else {
            googleMap.setMyLocationEnabled(true);
            locationManager = (LocationManager) getActivity().getSystemService(LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_REFRESH_TIME,
                    LOCATION_REFRESH_DISTANCE, locationListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_ACCESS_FINE_LOCATION_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    googleMap.setMyLocationEnabled(true);
                    locationManager = (LocationManager) getActivity().getSystemService(LOCATION_SERVICE);
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_REFRESH_TIME,
                            LOCATION_REFRESH_DISTANCE, locationListener);
                }
            }
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            LatLng streetArtLocation = GeoUtil.getCoordinates(getContext(), streetArt.getAddress());
            if(streetArtLocation != null) {
                double dist = SphericalUtil.computeDistanceBetween(new LatLng(location.getLatitude(), location.getLongitude()), streetArtLocation);
                String displayText = Math.round(dist) + " m away";
                if (dist > 1000) {
                    int distKm = (int) Math.round(dist/1000);
                    displayText = distKm + " km away";
                }
                distanceTextView.setText(displayText);
                distanceTextView.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };
}
