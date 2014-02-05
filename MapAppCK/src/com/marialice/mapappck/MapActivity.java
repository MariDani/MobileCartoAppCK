package com.marialice.mapappck;

/* 
 * this is the main activity, the map view
 */
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;

public class MapActivity extends FragmentActivity implements
		ConnectionCallbacks, OnConnectionFailedListener, LocationListener,
		OnMyLocationButtonClickListener, OnInfoWindowClickListener,
		OnMapLongClickListener {

	// include our classes
	TextToBitmap drawclass = new TextToBitmap();
	DatabaseContent dbclass = new DatabaseContent();

	private static final String MAPBOX_BASEMAP_URL_FORMAT = "http://api.tiles.mapbox.com/v3/maridani.h0a912jg/%d/%d/%d.png";
	private GoogleMap mMap;

	// For getting the location of the device
	private LocationClient mLocationClient;
	private static final LocationRequest REQUEST = LocationRequest.create()
			.setInterval(5000) // 5 seconds
			.setFastestInterval(16) // 16ms = 60fps
			.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// on create, some methods are called
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);
		setUpMapIfNeeded();
	}

	@Override
	protected void onResume() {
		super.onResume();
		setUpMapIfNeeded();
		setUpLocationClientIfNeeded();
		mLocationClient.connect();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mLocationClient != null) {
			mLocationClient.disconnect();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.map, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle presses on the action bar items
		switch (item.getItemId()) {
		case R.id.goto_actlikelocal:
			// start the pop up with a random hint
			Random randomi = new Random();
			int i = randomi.nextInt((9 - 0) + 1) + 0;
			// nextInt((max - min) + 1) + min;
			createPopUp(i);
			return true;
		case R.id.goto_legend:
			Intent legendintent = new Intent(MapActivity.this,
					LegendActivity.class);
			startActivity(legendintent);
			return true;
		case R.id.goto_about:
			Intent aboutintent = new Intent(MapActivity.this,
					AboutActivity.class);
			startActivity(aboutintent);
			return true;
		case R.id.goto_places:
			Intent placesintent = new Intent(MapActivity.this,
					PlacesActivity.class);
			startActivity(placesintent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void setUpMapIfNeeded() {
		// a null check to confirm that the map is not already instantiated
		if (mMap == null) {
			// Try to obtain the map from the SupportMapFragment.
			mMap = ((SupportMapFragment) getSupportFragmentManager()
					.findFragmentById(R.id.map)).getMap();
			// Check if we were successful in obtaining the map.
			if (mMap != null) {
				setUpMap();
				mMap.setMyLocationEnabled(true);
				mMap.setOnMyLocationButtonClickListener(this);
			}
		}
	}

	private void setUpMap() {
		// declare map properties
		mMap.setMapType(GoogleMap.MAP_TYPE_NONE);
		mMap.setMyLocationEnabled(true); // the location button
		mMap.getUiSettings().setZoomControlsEnabled(true); // the +/- buttons
		mMap.setOnInfoWindowClickListener(this);
		mMap.setOnMapLongClickListener(this);

		// the center coordinates and zoom level on the map
		Double default_lat = 48.8108144;
		Double default_lon = 14.3142367;
		Double lat = getIntent().getDoubleExtra("lat", default_lat);
		Double lon = getIntent().getDoubleExtra("lon", default_lon);
		if (lat.equals(default_lat)) {
			mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(
					default_lat, default_lon), 15));
		} else {
			// in case the coordinates come from the description button
			zoomFromDescription(lat, lon);
		}

		// create the tile overlay
		TileProvider tileProvider = new UrlTileProvider(256, 256) {
			@Override
			public synchronized URL getTileUrl(int x, int y, int zoom) {
				String s = String.format(Locale.US, MAPBOX_BASEMAP_URL_FORMAT,
						zoom, x, y);
				URL url = null;
				try {
					url = new URL(s);
				} catch (MalformedURLException e) {
					throw new AssertionError(e);
				}
				return url;
			}
		};
		// add the tile overlay to the map
		mMap.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));

		// call the method that creates the static markers
		addMarkersToMap();

		// create the markers from database
		List<Poi> dbpois = dbclass.queryDataFromDatabase(this);

		for (int i = 0; i < dbpois.size(); i++) {
			Poi poi = dbpois.get(i);
			mMap.addMarker(new MarkerOptions()
					.position(poi.getLatLng())
					.title(poi.getTitle())
					.snippet(poi.getCategoryName())
					.icon(BitmapDescriptorFactory.fromBitmap(drawclass
							.drawTextToBitmap(getApplicationContext(),
									poi.getSymbol(), poi.getNumber()))));
		}
	}

	// Zoom to city center button
	public void zoomCityCenter(View view) {
		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(
				48.8107106, 14.3149464), 16));
	}

	// Zoom to overview button
	public void zoomOverview(View view) {
		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(
				48.8142811, 14.3163144), 14));
	}

	// Zoom to selected poi from description
	public void zoomFromDescription(Double lat, Double lon) {
		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon),
				18));
	}

	// the method creates static markers, that are added to the map
	private void addMarkersToMap() {

		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.ck_hlediste))
				.position(new LatLng(48.8104739, 14.3076967)).anchor(0.6f, 0.3f)
				.title("Otáèivé hledištì").snippet("Open air auditorium")
				.flat(true).rotation(355).infoWindowAnchor(0.5f, 0.5f));

		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.ck_kostel))
				.position(new LatLng(48.8101733, 14.3161319)).anchor(0.5f, 0.7f)
				.title("Kostel sv. Víta").snippet("St.Vitus Church").flat(true)
				.rotation(355).infoWindowAnchor(0.5f, 0.5f));

		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.ck_most))
				.position(new LatLng(48.8117769, 14.3157628)).flat(true)
				.anchor(0.5f, 0.7f).rotation(315).title("Lazebnický most")
				.snippet("Barber's Bridge").infoWindowAnchor(0.6f, 0.7f));

		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.ck_most_galerie))
				.position(new LatLng(48.8125969, 14.3129864))
				.anchor(0.9f, 0.5f).rotation(350).title("Plášový most")
				.snippet("Cloak Bridge").flat(true)
				.infoWindowAnchor(0.5f, 0.5f));

		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.ck_muzeum))
				.position(new LatLng(48.8117853, 14.3163892))
				.anchor(0.5f, 0.9f).rotation(0).flat(true).title("Muzeum")
				.snippet("Museum").infoWindowAnchor(0.5f, 0.5f));

		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.ck_vez))
				.flat(true).position(new LatLng(48.8123042, 14.3159189))
				.anchor(0.8f, 1f).rotation(355).title("Zámecká vìž").snippet("Castle Tower")
				.infoWindowAnchor(0.5f, 0.5f));

		// people on boats
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.ck_kajakar)).flat(true)
				.position(new LatLng(48.8144053, 14.3190369))
				.anchor(0.5f, 0.75f).rotation(355));

		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.ck_kajakar)).flat(true)
				.position(new LatLng(48.8029367, 14.3147117))
				.anchor(0.5f, 0.75f).rotation(0));

		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.ck_raftaci)).flat(true)
				.position(new LatLng(48.8093228, 14.3152989))
				.anchor(0.5f, 0.75f).rotation(50));

		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.ck_vodaci)).flat(true)
				.position(new LatLng(48.8069317, 14.3148481))
				.anchor(0.5f, 0.75f).rotation(10));

		// Hospital poi
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_hospital)).flat(true)
				.position(new LatLng(48.8162561, 14.3156200)));

		// Sport poi
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_sport)).flat(true)
				.position(new LatLng(448.8155689, 14.3112294)));

		// Hostel poi
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_hostel)).flat(true)
				.position(new LatLng(48.8146692, 14.3172897)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_hostel)).flat(true)
				.position(new LatLng(48.8113350, 14.3140389)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_hostel)).flat(true)
				.position(new LatLng(48.8090311, 14.3139206)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_hostel)).flat(true)
				.position(new LatLng(48.8084631, 14.3205269)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_hostel)).flat(true)
				.position(new LatLng(48.8069489, 14.3132067)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_hostel)).flat(true)
				.position(new LatLng(48.8046750, 14.3135644)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_hostel)).flat(true)
				.position(new LatLng(48.8038872, 14.3142086)));

		// Post poi
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.poi_post))
				.flat(true).position(new LatLng(48.8140939, 14.3173522)));

		// Romantic walk poi
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_romantic)).flat(true)
				.position(new LatLng(48.8091658, 14.3183997)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_romantic)).flat(true)
				.position(new LatLng(48.8113103, 14.3099122)));

		// Infocentre poi
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.poi_info))
				.flat(true).position(new LatLng(48.8108389, 14.3153600)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.poi_info))
				.flat(true).position(new LatLng(48.8127761, 14.3173836)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.poi_info))
				.flat(true).position(new LatLng(48.8153258, 14.3127175)));

		// View poi
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.poi_view))
				.flat(true).position(new LatLng(48.8123017, 14.3125000)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.poi_view))
				.flat(true).position(new LatLng(48.8122175, 14.3134344)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.poi_view))
				.flat(true).position(new LatLng(48.8106389, 14.3172764)));
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.poi_view))
				.flat(true).position(new LatLng(48.8106233, 14.3184344)));

		// Grocery poi
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.poi_grocery)).flat(true)
				.position(new LatLng(48.8113175, 14.3156464)));

		// Bus station
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.bus_station)).flat(true).alpha(0.7f)
				.position(new LatLng(48.8116094, 14.3224528)).rotation(0));
		// Train station
		mMap.addMarker(new MarkerOptions()
				.icon(BitmapDescriptorFactory
						.fromResource(R.drawable.train_station)).flat(true).alpha(0.7f)
				.position(new LatLng(48.8225433, 14.3169514)).rotation(353));
	}

	// this is what happens when an info window is clicked
	@Override
	public void onInfoWindowClick(Marker marker) {
		String title = marker.getTitle();
		if (title.equals("Otáèivé hledištì") | title.equals("Kostel sv. Víta")
				| title.equals("Lazebnický most")
				| title.equals("Plášový most") | title.equals("Muzeum")
				| title.equals("Zámecká vìž")) {
			// do nothing
		} else {
			// start the description with extras in the intent
			Intent infowindowintent = new Intent(this,
					PlacesDescriptionActivity.class);
			infowindowintent.putExtra("listDataChild", title);
			startActivity(infowindowintent);
		}
	}

	// this method creates the pop up info when clicking the bulb icon
	private int createPopUp(int i) {

		List<String> hintlist = new ArrayList<String>();
		String text1 = getResources().getString(R.string.actlikealocal_text1);
		String text2 = getResources().getString(R.string.actlikealocal_text2);
		String text3 = getResources().getString(R.string.actlikealocal_text3);
		String text4 = getResources().getString(R.string.actlikealocal_text4);
		String text5 = getResources().getString(R.string.actlikealocal_text5);
		String text6 = getResources().getString(R.string.actlikealocal_text6);
		String text7 = getResources().getString(R.string.actlikealocal_text7);
		hintlist.add(text1);
		hintlist.add(text2);
		hintlist.add(text3);
		hintlist.add(text4);
		hintlist.add(text5);
		hintlist.add(text6);
		hintlist.add(text7);

		// Instantiate an AlertDialog.Builder with its constructor
		AlertDialog.Builder builder = new AlertDialog.Builder(
				new ContextThemeWrapper(this, R.style.PopUpHint));

		// Chain together various setter methods to set the dialog
		// characteristics
		final int count = hintlist.size();
		if (i < count && i >= 0) {
			String message = hintlist.get(i);
			int hintnumber = i + 1;
			builder.setTitle(R.string.actlikealocal)
					.setMessage(message + " (Hint " + hintnumber + "/7)")
					.setIcon(R.drawable.action_bulb_blue)
					.setNeutralButton(R.string.close,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									// User clicked 'close' button
								}
							});

			final int h = i - 1;
			builder.setNegativeButton(R.string.previous,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							// User clicked 'previous' button
							if (h >= 0) {
								createPopUp(h);
							} else {
								int g = count - 1;
								// start with the last hint after first is
								// displayed
								createPopUp(g);
							}
						}
					});

			final int j = i + 1;
			builder.setPositiveButton(R.string.next,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							// User clicked 'Next' button
							if (j < count) {
								createPopUp(j);
							} else {
								// start with the first hint after last is
								// displayed
								createPopUp(0);
							}
						}
					});

			// Get the AlertDialog from create()
			AlertDialog popup = builder.create();
			popup.show();

		} else {
			// for debugging only. the user should never get this toast :)
			Context context = getApplicationContext();
			CharSequence text = "something is wrong...";
			int duration = Toast.LENGTH_SHORT;
			Toast toast = Toast.makeText(context, text, duration);
			toast.show();
		}
		return i;
	}

	// on long click listener
	@Override
	public void onMapLongClick(LatLng point) {

		LayoutInflater inflater = getLayoutInflater();
		// Inflate the Layout
		View layout = inflater.inflate(R.layout.custom_toast_map,
				(ViewGroup) findViewById(R.id.custom_toast_layout));
		Toast toast = new Toast(getApplicationContext());
		toast.setDuration(Toast.LENGTH_LONG);
		toast.setGravity(Gravity.LEFT | Gravity.BOTTOM, 20, 20);
		toast.setView(layout);
		toast.show();
	}

	// the following methods are used for retrieving device's location
	private void setUpLocationClientIfNeeded() {
		if (mLocationClient == null) {
			// OnConnectionFailedListener
			mLocationClient = new LocationClient(getApplicationContext(), this,
					this);
		}
	}

	@Override
	public void onLocationChanged(Location location) {
		// do nothing
	}

	@Override
	public void onConnected(Bundle connectionHint) {
		// LocationListener
		mLocationClient.requestLocationUpdates(REQUEST, this);
	}

	@Override
	public void onDisconnected() {
		// do nothing
	}

	@Override
	public void onConnectionFailed(ConnectionResult result) {
		// do nothing
	}

	@Override
	public boolean onMyLocationButtonClick() {
		return false;
	}

}
