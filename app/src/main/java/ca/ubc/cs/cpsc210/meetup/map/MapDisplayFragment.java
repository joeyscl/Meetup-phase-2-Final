package ca.ubc.cs.cpsc210.meetup.map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.PathOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import ca.ubc.cs.cpsc210.meetup.R;
import ca.ubc.cs.cpsc210.meetup.model.Building;
import ca.ubc.cs.cpsc210.meetup.model.Course;
import ca.ubc.cs.cpsc210.meetup.model.CourseFactory;
import ca.ubc.cs.cpsc210.meetup.model.EatingPlace;
import ca.ubc.cs.cpsc210.meetup.model.Place;
import ca.ubc.cs.cpsc210.meetup.model.PlaceFactory;
import ca.ubc.cs.cpsc210.meetup.model.Section;
import ca.ubc.cs.cpsc210.meetup.model.Student;
import ca.ubc.cs.cpsc210.meetup.model.StudentManager;
import ca.ubc.cs.cpsc210.meetup.util.LatLon;
import ca.ubc.cs.cpsc210.meetup.util.SchedulePlot;

/**
 * Fragment holding the map in the UI.
 */
public class MapDisplayFragment extends Fragment {

    /**
     * Log tag for LogCat messages
     */
    private final static String LOG_TAG = "MapDisplayFragment";

    /**
     * Preference manager to access user preferences
     */
    private SharedPreferences sharedPreferences;

    /**
     * String to know whether we are dealing with MWF or TR schedule.
     * You will need to update this string based on the settings dialog at appropriate
     * points in time. See the project page for details on how to access
     * the value of a setting.
     */
    private String activeDay = "MWF";

    /**
     * A central location in campus that might be handy.
     */
    private final static GeoPoint UBC_MARTHA_PIPER_FOUNTAIN = new GeoPoint(49.264865,
            -123.252782);

    /**
     * Meetup Service URL
     * CPSC 210 Students: Complete the string.
     */
    private final String getStudentURL = "";

    /**
     * FourSquare URLs. You must complete the client_id and client_secret with values
     * you sign up for.
     */
    private static String FOUR_SQUARE_URL = "https://api.foursquare.com/v2/venues/explore";
    private static String FOUR_SQUARE_CLIENT_ID = "5CPDFDANZA5VV0BYRHWNW2NSGI0SIXVHCMI42DXKM3NTGZ5V";
    private static String FOUR_SQUARE_CLIENT_SECRET = "5T1ZMKPRMSYD5SIWDVQWIUEBPAUFUHOOW44DNCBY2NRTWYGH";
    private static Boolean getPlacesHasExecuted = false;

    private static String sushiId = "4bf58dd8d48988d1d2941735";
    private static String pubId = "4bf58dd8d48988d11b941735";
    private static String pizzaId = "4bf58dd8d48988d1ca941735";
    private static String cafeId = "4bf58dd8d48988d16d941735";

    /**
     * Overlays for displaying my schedules, buildings, etc.
     */
    private List<PathOverlay> scheduleOverlay;
    private ItemizedIconOverlay<OverlayItem> buildingOverlay;
    private OverlayItem selectedBuildingOnMap;

    /**
     * View that shows the map
     */
    private MapView mapView;

    /**
     * Access to domain model objects. Only store "me" in the studentManager for
     * the base project (i.e., unless you are doing bonus work).
     */
    private StudentManager studentManager;
    private Student randomStudent = null;
    private Set<Student> randomStudentsSet = new HashSet<Student>();
    private Student me = null;
    private static int ME_ID = 999999;

    /**
     * Map controller for zooming in/out, centering
     */
    private IMapController mapController;

    // ******************** Android methods for starting, resuming, ...

    // You should not need to touch this method
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        scheduleOverlay = new ArrayList<PathOverlay>();

        // You need to setup the courses for the app to know about. Ideally
        // we would access a web service like the UBC student information system
        // but that is not currently possible
        initializeCourses();

        // Initialize the data for the "me" schedule. Note that this will be
        // hard-coded for now
        initializeMySchedule();

        //initialize Places by querying Foursquare
        //initializePlaces();

        // You are going to need an overlay to draw buildings and locations on the map
        buildingOverlay = createBuildingOverlay();

        clearSchedules();
    }

    // You should not need to touch this method
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
    }

    // You should not need to touch this method
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if (mapView == null) {
            mapView = new MapView(getActivity(), null);

            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setClickable(true);
            mapView.setBuiltInZoomControls(true);
            mapView.setMultiTouchControls(true);

            mapController = mapView.getController();
            mapController.setZoom(mapView.getMaxZoomLevel() - 2);
            mapController.setCenter(UBC_MARTHA_PIPER_FOUNTAIN);
        }

        return mapView;
    }

    // You should not need to touch this method
    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView");
        ((ViewGroup) mapView.getParent()).removeView(mapView);
        super.onDestroyView();
    }

    // You should not need to touch this method
    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    // You should not need to touch this method
    @Override
    public void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
    }

    // You should not need to touch this method
    @Override
    public void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
    }

    /**
     * Save map's zoom level and centre. You should not need to
     * touch this method
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);

        if (mapView != null) {
            outState.putInt("zoomLevel", mapView.getZoomLevel());
            IGeoPoint cntr = mapView.getMapCenter();
            outState.putInt("latE6", cntr.getLatitudeE6());
            outState.putInt("lonE6", cntr.getLongitudeE6());
            Log.i("MapSave", "Zoom: " + mapView.getZoomLevel());
        }
    }

    // ****************** App Functionality

    /**
     * Show my schedule on the map. Every time "me"'s schedule shows, the map
     * should be cleared of all existing schedules, buildings, meetup locations, etc.
     */
    public void showMySchedule() {

        // CPSC 210 Students: You must complete the implementation of this method.
        // The very last part of the method should call the asynchronous
        // task (which you will also write the code for) to plot the route
        // for "me"'s schedule for the day of the week set in the Settings

        // Asynchronous tasks are a bit onerous to deal with. In order to provide
        // all information needed in one object to plot "me"'s route, we
        // create a SchedulePlot object and pass it to the asynchrous task.
        // See the project page for more details.


        // Get a routing between these points. This line of code creates and calls
        // an asynchronous task to do the calls to MapQuest to determine a route
        // and plots the route.
        // Assumes mySchedulePlot is a create and initialized SchedulePlot object

        // UNCOMMENT NEXT LINE ONCE YOU HAVE INSTANTIATED mySchedulePlot
        //  new GetRoutingForSchedule().execute(mySchedulePlot);
        clearSchedules();
        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        SortedSet<Section> mySection = me.getSchedule().getSections(dayOfWeek);
        SchedulePlot myPlot = new SchedulePlot(mySection, me.getFirstName(),"black" , R.drawable.ic_action_place);
        new GetRoutingForSchedule().execute(myPlot);

    }

    /**
     * Retrieve a random student's schedule from the Meetup web service and
     * plot a route for the schedule on the map. The plot should be for
     * the given day of the week as determined when "me"'s schedule
     * was plotted.
     */
    public void showRandomStudentsSchedule() {
        // To get a random student's schedule, we have to call the MeetUp web service.
        // Calling this web service requires a network access to we have to
        // do this in an asynchronous task. See below in this class for where
        // you need to implement methods for performing the network access
        // and plotting.
        new GetRandomSchedule().execute();

    }

    /**
     * Clear all schedules on the map
     */
    public void clearSchedules() {
        randomStudent = null;
        randomStudentsSet = new HashSet<>();
        initializeMySchedule();
        OverlayManager om = mapView.getOverlayManager();
        om.clear();
        scheduleOverlay.clear();
        buildingOverlay.removeAllItems();
        om.addAll(scheduleOverlay);
        om.add(buildingOverlay);
        mapView.invalidate();
    }

    /**
     * Find all possible locations at which "me" and random student could meet
     * up for the set day of the week and the set time to meet and the set
     * distance either "me" or random is willing to travel to meet.
     * A meetup is only possible if both "me" and random are free at the
     * time specified in the settings and each of us must have at least an hour
     * (>= 60 minutes) free. You should display dialog boxes if there are
     * conditions under which no meetup can happen (e.g., me or random is
     * in class at the specified time)
     */
    public void findMeetupPlace() {
        // CPSC 210 students: you must complete this method

        //check if food places have been loaded, exit if it hasn't
        if (getPlacesHasExecuted != true) {
            AlertDialog aDialog = createSimpleDialog("Please click 'Get Places'.");
            aDialog.show();
            return;
        }

        //initialize useful variables
        boolean canMeetUp = true; //local variable signifying whether students can meet or not
        String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");
        String meetUpTime = sharedPreferences.getString("timeOfDay", "12:00") + ":00";
        int distance = Integer.valueOf(sharedPreferences.getString("placeDistance", "2000"));
        //int distance = 500;
        //initializing a set of all students that are meeting up
        Set<Student> studentsForMeetUp = new HashSet<Student>();
        studentsForMeetUp.add(me);
        for (Student S : randomStudentsSet) {
            if (S.getSchedule().getSections(dayOfWeek).size() != 0) { //only add students that have class since everyone else is flexible
                studentsForMeetUp.add(S);
            }
        }
        //testing correctness of new methods in Schedule class
        //boolean amIFree = me.getSchedule().breakAtThisTime(dayOfWeek, meetUpTime);

        //determine if all students are free at meetUpTime
        for (Student S : studentsForMeetUp) {
            if (S.getSchedule().breakAtThisTime(dayOfWeek, meetUpTime) == false) {
                canMeetUp = false;
                break;
            }
        }

        //exit conditions
        if (studentsForMeetUp.size() <= 1) { //exit method if not enough students have class 
            AlertDialog aDialog = createSimpleDialog("You're a loner with no friends.");
            aDialog.show();
            return;
        }else if (canMeetUp == false) { //exit method if cannot meetUp
            AlertDialog aDialog = createSimpleDialog("Common Break404 not Found. Cannot MeetUp.");
            aDialog.show();
            return;
        }

        String foodType = sharedPreferences.getString("foodType","");

        //determine set of possible meetUp places within walking distance from me
        LatLon whereAmILatLon = me.getSchedule().whereAmI(dayOfWeek, meetUpTime).getLatLon();
        Set<Place> possiblePlaces = PlaceFactory.getInstance().findPlacesWithinDistance(whereAmILatLon, distance);

        //filter places based on foodType
        Set<Place> foodPlaces = new HashSet<Place>();
        if (foodType.equals("none")) {
            for (Place P : possiblePlaces) {
                if (P.containsTag("Food")) {
                    foodPlaces.add(P);
                }
            }
        } else {
            for (Place P : possiblePlaces) {
                if (P.containsTag(foodType)) {
                    foodPlaces.add(P);
                }
            }
        }

        //everything is too far
        if (foodPlaces.size() == 0) {
            AlertDialog bDialog = createSimpleDialog("Walking is overrated. Widen search range");
            bDialog.show();
            return;
        }

        //filter to get a Set of places all students can meet
        Set<Place> meetUpPlaces = new HashSet<Place>();
        for (Place P : foodPlaces) {
            boolean allWithinDistance = true;
            for (Student S : studentsForMeetUp) {
                LatLon studentLatLon = S.getSchedule().whereAmI(dayOfWeek, meetUpTime).getLatLon();
                if (LatLon.distanceBetweenTwoLatLon(studentLatLon, P.getLatLon()) > distance) {
                    allWithinDistance = false;
                    break;
                }
            }
            if (allWithinDistance) {
                meetUpPlaces.add(P);
            }
        }

        //exit if distance is too far for all places for some students
        if (meetUpPlaces.size() == 0) {
            AlertDialog cDialog = createSimpleDialog("Your friends deem you unworthy of their walking");
            cDialog.show();
            return;
        }

        //meetUp places within walking distance have been determined, plot these places
        for (Place S: meetUpPlaces) {
            Building B = new Building(S.getName(),S.getLatLon()); //change S to a building to plot it
            plotABuilding(B, S.getName(), S.getPhone(), R.drawable.ic_action_restaurant);
        }
        mapView.invalidate();
    }

    /**
     * Initialize the PlaceFactory with information from FourSquare
     */
    public void initializePlaces() {
        // CPSC 210 Students: You should not need to touch this method, but
        // you will have to implement GetPlaces below.
        getPlacesHasExecuted = true;
        new GetPlaces().execute();
    }


    /**
     * Plot all buildings referred to in the given information about plotting
     * a schedule.
     * @param schedulePlot All information about the schedule and route to plot.
     */
    private void plotBuildings(SchedulePlot schedulePlot) {

        // CPSC 210 Students: Complete this method by plotting each building in the
        // schedulePlot with an appropriate message displayed
        SortedSet<Section> sections = schedulePlot.getSections();
        for (Section S : sections) {
            plotABuilding(S.getBuilding(),S.getBuilding().getName(),S.getCourse().getCode()+ " " +S.getCourse().getNumber()+" at "+S.getCourseTime().getStartTime(), R.drawable.ic_action_place);
        }

        // CPSC 210 Students: You will need to ensure the buildingOverlay is in
        // the overlayManager. The following code achieves this. You should not likely
        // need to touch it
        OverlayManager om = mapView.getOverlayManager();
        om.add(buildingOverlay);

    }

    /**
     * Plot a building onto the map
     * @param building The building to put on the map
     * @param title The title to put in the dialog box when the building is tapped on the map
     * @param msg The message to display when the building is tapped
     * @param drawableToUse The icon to use. Can be R.drawable.ic_action_place (or any icon in the res/drawable directory)
     */
    private void plotABuilding(Building building, String title, String msg, int drawableToUse) {
        // CPSC 210 Students: You should not need to touch this method
        OverlayItem buildingItem = new OverlayItem(title, msg,
                new GeoPoint(building.getLatLon().getLatitude(), building.getLatLon().getLongitude()));

        //Create new marker
        Drawable icon = this.getResources().getDrawable(drawableToUse);

        //Set the bounding for the drawable
        icon.setBounds(
                0 - icon.getIntrinsicWidth() / 2, 0 - icon.getIntrinsicHeight(),
                icon.getIntrinsicWidth() / 2, 0);

        //Set the new marker to the overlay
        buildingItem.setMarker(icon);
        buildingOverlay.addItem(buildingItem);
    }



    /**
     * Initialize your schedule by coding it directly in. This is the schedule
     * that will appear on the map when you select "Show My Schedule".
     */
    private void initializeMySchedule() {
        // CPSC 210 Students; Implement this method
        studentManager = new StudentManager();
        studentManager.addStudent("Lee", "Joey", 999999);
        studentManager.addSectionToSchedule(999999, "CPSC", 210, "BCS");
        studentManager.addSectionToSchedule(999999, "MATH", 200, "201");
        studentManager.addSectionToSchedule(999999, "PHYS", 203, "201");
        studentManager.addSectionToSchedule(999999, "MATH", 221, "202");

        me = studentManager.get(999999);
    }

    /**
     * Helper to create simple alert dialog to display message
     *
     * @param msg message to display in alert dialog
     * @return the alert dialog
     */
    private AlertDialog createSimpleDialog(String msg) {
        // CPSC 210 Students; You should not need to modify this method
        AlertDialog.Builder dialogBldr = new AlertDialog.Builder(getActivity());
        dialogBldr.setMessage(msg);
        dialogBldr.setNeutralButton(R.string.ok, null);

        return dialogBldr.create();
    }

    /**
     * Create the overlay used for buildings. CPSC 210 students, you should not need to
     * touch this method.
     * @return An overlay
     */
    private ItemizedIconOverlay<OverlayItem> createBuildingOverlay() {
        ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

        ItemizedIconOverlay.OnItemGestureListener<OverlayItem> gestureListener =
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

                    /**
                     * Display building description in dialog box when user taps stop.
                     *
                     * @param index
                     *            index of item tapped
                     * @param oi
                     *            the OverlayItem that was tapped
                     * @return true to indicate that tap event has been handled
                     */
                    @Override
                    public boolean onItemSingleTapUp(int index, OverlayItem oi) {

                        new AlertDialog.Builder(getActivity())
                                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface arg0, int arg1) {
                                        if (selectedBuildingOnMap != null) {
                                            mapView.invalidate();
                                        }
                                    }
                                }).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
                                .show();

                        selectedBuildingOnMap = oi;
                        mapView.invalidate();
                        return true;
                    }

                    @Override
                    public boolean onItemLongPress(int index, OverlayItem oi) {
                        // do nothing
                        return false;
                    }
                };

        return new ItemizedIconOverlay<OverlayItem>(
                new ArrayList<OverlayItem>(), getResources().getDrawable(
                R.drawable.ic_action_place), gestureListener, rp);
    }


    /**
     * Create overlay with a specific color
     * @param colour A string with a hex colour value
     */
    private PathOverlay createPathOverlay(String colour) {
        // CPSC 210 Students, you should not need to touch this method
        PathOverlay po = new PathOverlay(Color.parseColor(colour),
                getActivity());
        Paint pathPaint = new Paint();
        pathPaint.setColor(Color.parseColor(colour));
        pathPaint.setStrokeWidth(4.0f);
        pathPaint.setStyle(Paint.Style.STROKE);
        po.setPaint(pathPaint);
        return po;
    }

    // *********************** Asynchronous tasks

    /**
     * This asynchronous task is responsible for contacting the Meetup web service
     * for the schedule of a random student. The task must plot the retrieved
     * student's route for the schedule on the map in a different colour than the "me" schedule
     * or must display a dialog box that a schedule was not retrieved.
     */
    private class GetRandomSchedule extends AsyncTask<Void, Void, SchedulePlot> {

        // Some overview explanation of asynchronous tasks is on the project web page.

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(Void... params) {

            // CPSC 210 Students: You must complete this method. It needs to
            // contact the Meetup web service to get a random student's schedule.
            // If it is successful in retrieving a student and their schedule,
            // it needs to remember the student in the randomStudent field
            // and it needs to create and return a schedulePlot object with
            // all relevant information for being ready to retrieve the route
            // and plot the route for the schedule. If no random student is
            // retrieved, return null.
            //
            // Note, leave all determination of routing and plotting until
            // the onPostExecute method below.

            ArrayList<GeoPoint> randomGeoPoints = new ArrayList<GeoPoint>();
            Student randomStudent = null;
            boolean randomStudentHasClassToday = false;
            String dayOfWeek = sharedPreferences.getString("dayOfWeek", "MWF");

            //loop until a random student with class on the day has been found
            while (randomStudentHasClassToday == false) {
                try {
                    //String meetUpResponse = makeRoutingCall("http://kramer.nss.cs.ubc.ca:8081/getStudent");
                    String meetUpResponse = makeRoutingCall("http://kramer.nss.cs.ubc.ca:8082/getStudent");

                    JSONTokener tokener = new JSONTokener(meetUpResponse);
                    JSONObject randomStudentObject = new JSONObject(tokener);

                    String randomFirstName = (String) randomStudentObject.get("FirstName");
                    String randomLastName = (String) randomStudentObject.get("LastName");
                    String randomIdAsString = (String) randomStudentObject.get("Id");
                    int randomId = Integer.valueOf(randomIdAsString);

                    //Log.d("firstName",randomFirstName);
                    //Log.d("lastName",randomLastName);
                    //Log.d("randomID",""+randomID);

                    JSONArray randomSectionsArray = randomStudentObject.getJSONArray("Sections");

                    studentManager.addStudent(randomLastName, randomFirstName, randomId);
                    randomStudent = studentManager.get(randomId);
                    randomStudentsSet.add(randomStudent);

                    for (int i = 0; i < randomSectionsArray.length(); i++) {

                        JSONObject currSection = new JSONObject();
                        currSection = randomSectionsArray.getJSONObject(i);

                        String courseName = (String) currSection.get("CourseName");
                        String courseNumberAsString = (String) currSection.get("CourseNumber");
                        int courseNumber = Integer.valueOf(courseNumberAsString);
                        String sectionName = (String) currSection.get("SectionName");

                        studentManager.addSectionToSchedule(randomId, courseName, courseNumber, sectionName);
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                SortedSet<Section> randomSections = randomStudent.getSchedule().getSections(dayOfWeek);
                if (randomSections.size() == 0) { //no classes today
                    continue;
                } else {
                    randomStudentHasClassToday = true;
                }

                //plot buildings moved from onPostExecute for instance where there is only 1 class
                if (randomSections.size() == 1) {
                    for (Section S : randomSections) {
                        plotABuilding(S.getBuilding(), S.getBuilding().getName(), S.getCourse().getCode() + " " + S.getCourse().getCode() + " at " + S.getCourseTime().getStartTime(), R.drawable.ic_action_place);
                    }
                }

                Random numGen = new Random();
                int r = numGen.nextInt(150);
                int g = numGen.nextInt(150);
                int b = numGen.nextInt(150);
                String hexColor = String.format("#%02x%02x%02x", r, g, b);

                SchedulePlot randomToPlot = new SchedulePlot(randomSections, randomStudent.getFirstName(), hexColor, R.drawable.ic_action_place);

                String fromAndTos = "";
                for (Section S : randomSections) {
                    fromAndTos += String.valueOf(S.getBuilding().getLatLon().getLatitude()) + "," + String.valueOf(S.getBuilding().getLatLon().getLongitude());
                    fromAndTos += "&to=";
                }
                //Log.i("SectionLatLon", ""+fromAndTos);
                try {
                    String mapQuestURL = "http://open.mapquestapi.com/directions/v2/route?key=Fmjtd%7Cluu82lu1ng%2Caa%3Do5-948wdr" +
                            "&outFormat=json&routeType=pedestrian&timeType=1&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=m" +
                            "&from=" + fromAndTos;

                    String mapQuestResponse = makeRoutingCall(mapQuestURL);

                    JSONTokener token = new JSONTokener(mapQuestResponse);
                    JSONObject object = new JSONObject(token);

                    JSONObject route = (JSONObject) object.get("route");
                    JSONObject shape = (JSONObject) route.get("shape");
                    JSONArray shapePoints = shape.getJSONArray("shapePoints");


                    for (int i = 0; i < shapePoints.length(); i += 2) {
                        GeoPoint aGeoPoint = new GeoPoint((double) shapePoints.get(i), (double) shapePoints.get(i + 1));
                        randomGeoPoints.add(aGeoPoint);
                    }

                    randomToPlot.setRoute(randomGeoPoints);
                    return randomToPlot;

                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }


        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {
            // CPSC 210 students: When this method is called, it will be passed
            // whatever schedulePlot object you created (if any) in doBackground
            // above. Use it to plot the route.

            if (schedulePlot == null) {
                mapView.invalidate();
                return;
            }

            PathOverlay po = createPathOverlay(schedulePlot.getColourOfLine());
            for (GeoPoint G: schedulePlot.getRoute()) {
                po.addPoint(G);
            }
            scheduleOverlay.add(po);
            OverlayManager om = mapView.getOverlayManager();
            om.addAll(scheduleOverlay);
            plotBuildings(schedulePlot);
            mapView.invalidate(); // cause map to redraw
        }
    }

    /**
     * This asynchronous task is responsible for contacting the MapQuest web service
     * to retrieve a route between the buildings on the schedule and for plotting any
     * determined route on the map.
     */
    private class GetRoutingForSchedule extends AsyncTask<SchedulePlot, Void, SchedulePlot> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(SchedulePlot... params) {

            // The params[0] element contains the schedulePlot object
            SchedulePlot scheduleToPlot = params[0];
            ArrayList<GeoPoint> myGeoPoints = new ArrayList<GeoPoint>();

            // CPSC 210 Students: Complete this method. This method should
            // call the MapQuest webservice to retrieve a List<GeoPoint>
            // that forms the routing between the buildings on the
            // schedule. The List<GeoPoint> should be put into
            // scheduleToPlot object.

            String fromAndTos = "";
            for (Section S: scheduleToPlot.getSections()) {
                fromAndTos += String.valueOf(S.getBuilding().getLatLon().getLatitude() )+","+ String.valueOf(S.getBuilding().getLatLon().getLongitude());
                fromAndTos += "&to=";
            }
            //Log.i("SectionLatLon", ""+fromAndTos);

            String mapQuestURL = "http://open.mapquestapi.com/directions/v2/route?key=Fmjtd%7Cluu82lu1ng%2Caa%3Do5-948wdr" +
                    "&outFormat=json&routeType=pedestrian&timeType=1&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=m" +
                    "&from="+fromAndTos;

            //String mapQuestURL = "http://open.mapquestapi.com/directions/v2/route?key=Fmjtd%7Cluu82lu1ng%2Caa%3Do5-948wdr&outFormat=json&routeType=pedestrian&timeType=1&enhancedNarrative=false&shapeFormat=raw&generalize=0&locale=en_US&unit=m&from=49.262866,-123.25323&to=49.266112,-123.254776&to=";


            try {
                String mapQuestResponse = makeMapQuestCall(mapQuestURL);

                JSONTokener token = new JSONTokener(mapQuestResponse);
                JSONObject object = new JSONObject(token);

                JSONObject route = (JSONObject) object.get("route");
                JSONObject shape = (JSONObject) route.get("shape");
                JSONArray shapePoints = shape.getJSONArray("shapePoints");


                for( int i = 0; i < shapePoints.length(); i += 2) {
                    GeoPoint aGeoPoint = new GeoPoint((double) shapePoints.get(i), (double) shapePoints.get(i+1) );
                    myGeoPoints.add(aGeoPoint);
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            scheduleToPlot.setRoute(myGeoPoints);
            return scheduleToPlot;
        }


        /**
         * An example helper method to call a web service
         */
        private String makeMapQuestCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }

        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {

            // CPSC 210 Students: This method should plot the route onto the map
            // with the given line colour specified in schedulePlot. If there is
            // no route to plot, a dialog box should be displayed.

//            To actually make something show on the map, you can use overlays.
//            For instance, the following code should show a line on a map

            PathOverlay po = createPathOverlay(schedulePlot.getColourOfLine());
            for (GeoPoint G: schedulePlot.getRoute()) {
                po.addPoint(G);
            }
            scheduleOverlay.add(po);
            OverlayManager om = mapView.getOverlayManager();
            om.addAll(scheduleOverlay);
            plotBuildings(schedulePlot);
            mapView.invalidate(); // cause map to redraw

        }
    }
    /**
     * This asynchronous task is responsible for contacting the FourSquare web service
     * to retrieve all places around UBC that have to do with food. It should load
     * any determined places into PlaceFactory and then display a dialog box of how it did
     */
    private class GetPlaces extends AsyncTask<Void, Void, String> {

        PlaceFactory placeFactory = PlaceFactory.getInstance();
        protected String doInBackground(Void... params) {
            // CPSC 210 Students: Complete this method to retrieve a string
            // of JSON from FourSquare. Return the string from this method

            String foursquareURL = "https://api.foursquare.com/v2/venues/explore?ll=49.26061,-123.24599%20%20&section=food"
                    + "&radius=3000&limit=50&v=20140101&client_id="+FOUR_SQUARE_CLIENT_ID+"&client_secret="+FOUR_SQUARE_CLIENT_SECRET;
            String foursquareResponse = "";
            try {
                foursquareResponse = makeFoursquareCall(foursquareURL);
                return foursquareResponse;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return foursquareResponse;
        }

        private String makeFoursquareCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }

        protected void onPostExecute(String jSONOfPlaces) {

            // CPSC 210 Students: Given JSON from FourQuest, parse it and load
            // PlaceFactory

            if (jSONOfPlaces == "") {
                return;
            }

            try {
                JSONTokener token = new JSONTokener(jSONOfPlaces);
                JSONObject response = new JSONObject(token).getJSONObject("response");
                JSONArray venues = response.getJSONArray("groups").getJSONObject(0).getJSONArray("items"); //venues is "items" in the JSON response

                //declaring variables
                JSONObject currVenue;
                JSONObject venueLocation;
                String venueName;
                Double venueLat;
                Double venueLon;
                LatLon venueLatLon;
                String formattedPhone;

                for (int i = 0; i < venues.length(); i++){

                    currVenue = venues.getJSONObject(i).getJSONObject("venue");
                    venueName = currVenue.getString("name"); //NEED THIS for new Place

                    venueLocation = currVenue.getJSONObject("location");
                    venueLat = venueLocation.getDouble("lat");
                    venueLon = venueLocation.getDouble("lng");

                    venueLatLon = new LatLon(venueLat,venueLon); //NEED THIS for new Place
                    EatingPlace currEatingPlace = new EatingPlace(venueName, venueLatLon);

                    try {
                        formattedPhone = currVenue.getJSONObject("contact").getString("formattedPhone");
                        currEatingPlace.addPhone(formattedPhone);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    placeFactory.add(currEatingPlace);

                    //Plot EatingPlace as Building!!!
//                    Building venueAsBuilding = new Building(venueName, venueLatLon);
//                    plotABuilding(venueAsBuilding, venueName, "Eat here!", R.drawable.ic_action_location_found);
                }

                //Query places with tags by calling GetPlacesOfFoodType AsyncTask
                Set<List<String>> foodTypes = new HashSet<>();
                foodTypes.add( Arrays.asList("sushi",sushiId));
                foodTypes.add( Arrays.asList("pub",pubId));
                foodTypes.add( Arrays.asList("pizza",pizzaId));
                foodTypes.add( Arrays.asList("cafe",cafeId));
                for (List<String> F : foodTypes) {
                    new GetPlacesOfFoodType().execute(F);
                }

                AlertDialog aDialog = createSimpleDialog("Added "+ venues.length() + " Food places." );
                aDialog.show();


                // CPSC 210 Students: You will need to ensure the buildingOverlay is in
                // the overlayManager. The following code achieves this. You should not likely
                // need to touch it
                OverlayManager om = mapView.getOverlayManager();
                om.add(buildingOverlay);



            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private class GetPlacesOfFoodType extends AsyncTask<List<String>, Void, List<String>> {

        PlaceFactory placeFactory = PlaceFactory.getInstance();
        protected List<String> doInBackground(List<String>... params) {
            // CPSC 210 Students: Complete this method to retrieve a string
            // of JSON from FourSquare. Return the string from this method


            String foodType = params[0].get(0);
            String categoryId = params[0].get(1);
            
            //I want to pass both the params (foodType, categoryId) and Foursquare response as list
            //foodType at index 0, categoryId at index 1, response at index 2
            List<String> doInBackgroundResponse = new ArrayList<>();
            doInBackgroundResponse.add(foodType); doInBackgroundResponse.add(categoryId);

            String foursquareURL = "https://api.foursquare.com/v2/venues/search?categoryId="+categoryId+"&ll=49.26061,-123.24599%20"
                    + "&radius=3000&limit=10&v=20140101&client_id="+FOUR_SQUARE_CLIENT_ID+"&client_secret="+FOUR_SQUARE_CLIENT_SECRET;
            String foursquareResponse = "";
            try {
                foursquareResponse = makeFoursquareCall(foursquareURL);
                doInBackgroundResponse.add(foursquareResponse);
                return doInBackgroundResponse;
            } catch (IOException e) {
                e.printStackTrace();
            }
            doInBackgroundResponse.add(foursquareResponse);
            return doInBackgroundResponse;
        }

        private String makeFoursquareCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }

        protected void onPostExecute(List<String> doInBackgroundResponse) {

            // CPSC 210 Students: Given JSON from FourQuest, parse it and load
            // PlaceFactory
            String foodType = doInBackgroundResponse.get(0);
            String categoryId = doInBackgroundResponse.get(1); //not used ...but for consistency sakes
            String jSONOfPlaces = doInBackgroundResponse.get(2);

            if (jSONOfPlaces == "") {
                return;
            }

            try {
                JSONTokener token = new JSONTokener(jSONOfPlaces);
                JSONObject response = new JSONObject(token).getJSONObject("response");
                JSONArray venues = response.getJSONArray("venues"); //venues is "items" in the JSON response

                //declaring variables
                JSONObject currVenue;
                String venueName;
                JSONObject venueLocation;
                Double venueLat;
                Double venueLon;
                LatLon venueLatLon;
                String formattedPhone;

                for (int i = 0; i < venues.length(); i++){

                    currVenue = venues.getJSONObject(i);
                    venueName = currVenue.getString("name"); //NEED THIS for new Place

                    venueLocation = currVenue.getJSONObject("location");
                    venueLat = venueLocation.getDouble("lat");
                    venueLon = venueLocation.getDouble("lng");

                    venueLatLon = new LatLon(venueLat,venueLon); //NEED THIS for new Place
                    EatingPlace currEatingPlace = new EatingPlace(venueName, venueLatLon);
                    currEatingPlace.addTag(foodType);

                    try {
                        formattedPhone = currVenue.getJSONObject("contact").getString("formattedPhone");
                        currEatingPlace.addPhone(formattedPhone);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    placeFactory.add(currEatingPlace);

                    //Plot EatingPlace as Building!!!
//                    Building venueAsBuilding = new Building(venueName, venueLatLon);
//                    plotABuilding(venueAsBuilding, venueName, "Eat here!", R.drawable.ic_action_location_found);
                }

                // CPSC 210 Students: You will need to ensure the buildingOverlay is in
                // the overlayManager. The following code achieves this. You should not likely
                // need to touch it

//                OverlayManager om = mapView.getOverlayManager();
//                om.add(buildingOverlay);


            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Initialize the CourseFactory with some courses.
     */
    private void initializeCourses() {
        // CPSC 210 Students: You can change this data if you desire.
        CourseFactory courseFactory = CourseFactory.getInstance();

        Building dmpBuilding = new Building("DMP", new LatLon(49.261474, -123.248060));

        Course cpsc210 = courseFactory.getCourse("CPSC", 210);
        Section aSection = new Section("202", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("201", "MWF", "16:00", "16:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("BCS", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);

        Course engl222 = courseFactory.getCourse("ENGL", 222);
        aSection = new Section("007", "MWF", "14:00", "14:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        engl222.addSection(aSection);
        aSection.setCourse(engl222);

        Course scie220 = courseFactory.getCourse("SCIE", 220);
        aSection = new Section("200", "MWF", "15:00", "15:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie220.addSection(aSection);
        aSection.setCourse(scie220);

        Course math200 = courseFactory.getCourse("MATH", 200);
        aSection = new Section("201", "MWF", "09:00", "09:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        math200.addSection(aSection);
        aSection.setCourse(math200);

        Course fren102 = courseFactory.getCourse("FREN", 102);
        aSection = new Section("202", "MWF", "11:00", "11:50", new Building("Barber", new LatLon(49.267442,-123.252471)));
        fren102.addSection(aSection);
        aSection.setCourse(fren102);

        Course japn103 = courseFactory.getCourse("JAPN", 103);
        aSection = new Section("002", "MWF", "10:00", "11:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        japn103.addSection(aSection);
        aSection.setCourse(japn103);

        Course scie113 = courseFactory.getCourse("SCIE", 113);
        aSection = new Section("213", "MWF", "13:00", "13:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie113.addSection(aSection);
        aSection.setCourse(scie113);

        Course micb308 = courseFactory.getCourse("MICB", 308);
        aSection = new Section("201", "MWF", "12:00", "12:50", new Building("Woodward", new LatLon(49.264704,-123.247536)));
        micb308.addSection(aSection);
        aSection.setCourse(micb308);

        Course math221 = courseFactory.getCourse("MATH", 221);
        aSection = new Section("202", "TR", "11:00", "12:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        math221.addSection(aSection);
        aSection.setCourse(math221);

        Course phys203 = courseFactory.getCourse("PHYS", 203);
        aSection = new Section("201", "TR", "09:30", "10:50", new Building("Hennings", new LatLon(49.266400,-123.252047)));
        phys203.addSection(aSection);
        aSection.setCourse(phys203);

        Course crwr209 = courseFactory.getCourse("CRWR", 209);
        aSection = new Section("002", "TR", "12:30", "13:50", new Building("Geography", new LatLon(49.266039,-123.256129)));
        crwr209.addSection(aSection);
        aSection.setCourse(crwr209);

        Course fnh330 = courseFactory.getCourse("FNH", 330);
        aSection = new Section("002", "TR", "15:00", "16:20", new Building("MacMillian", new LatLon(49.261167,-123.251157)));
        fnh330.addSection(aSection);
        aSection.setCourse(fnh330);

        Course cpsc499 = courseFactory.getCourse("CPSC", 430);
        aSection = new Section("201", "TR", "16:20", "17:50", new Building("Liu", new LatLon(49.267632,-123.259334)));
        cpsc499.addSection(aSection);
        aSection.setCourse(cpsc499);

        Course chem250 = courseFactory.getCourse("CHEM", 250);
        aSection = new Section("203", "TR", "10:00", "11:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        chem250.addSection(aSection);
        aSection.setCourse(chem250);

        Course eosc222 = courseFactory.getCourse("EOSC", 222);
        aSection = new Section("200", "TR", "11:00", "12:20", new Building("ESB", new LatLon(49.262866, -123.25323)));
        eosc222.addSection(aSection);
        aSection.setCourse(eosc222);

        Course biol201 = courseFactory.getCourse("BIOL", 201);
        aSection = new Section("201", "TR", "14:00", "15:20", new Building("BioSci", new LatLon(49.263920, -123.251552)));
        biol201.addSection(aSection);
        aSection.setCourse(biol201);
    }

}
