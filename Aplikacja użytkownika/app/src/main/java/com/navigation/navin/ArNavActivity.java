package com.navigation.navin;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.AnimationData;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;

import android.support.v4.app.FragmentActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ArNavActivity extends FragmentActivity implements View.OnClickListener {
    private static final String TAG = ArNavActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;
    final Context context = this;

    // ARcore stuff
    private ArFragment arFragment;
    private ModelRenderable sphereRenderable;

    //Anchors stuff
    private int index_;
    private int alpha_before=0;
    private Boolean type_of_point = false; // true - worker, false - room

    private String room_no = "";
    private String start_point = "";
    private String end_point ="";
    private String path_to;
    private String worker;

    private List <MyNode> neighbours_nodes_list = new ArrayList<>();
    private List <MyNode> nodes_after_search = new ArrayList<>();
    private List <Node> copied_nodes_list = new ArrayList<>();

    //Database stuff
    private DatabaseReference mDatabase;
    private DataSnapshot mainDataSnapshot;
    private List <String> roomList = new ArrayList<>();
    private List <Workers> workers_list = new ArrayList<>();
    private List <String> workers_names = new ArrayList<>();

    //Android stuff
    private SeekBar seekbar;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})

    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ux);

        seekbar = findViewById(R.id.sb_angle);
        seekbar.setMax(360);
        seekbar.setProgress(0);

        openTutorial();

        // Okay button, starts thread
        ImageButton startButton = findViewById(R.id.ib_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateNodes();
                seekbar.setVisibility(View.INVISIBLE);
            }
        });

        // D Open choose worker/room dialog
        ImageButton butt = findViewById(R.id.ib_choose);
        butt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Dialog dialog = new Dialog(context);
                dialog.setContentView(R.layout.choose_room_ux);

                Button okButton = dialog.findViewById(R.id.accept_button);
                RadioGroup radioGroup;
                RadioButton rb_worker;
                RadioButton rb_room;

                TextView tv_worker = dialog.findViewById(R.id.tv_worker);
                TextView tv_phone_no = dialog.findViewById(R.id.tv_phone_no);
                TextView tv_monday = dialog.findViewById(R.id.tv_monday);
                TextView tv_tuesday = dialog.findViewById(R.id.tv_tuesday);
                TextView tv_wednesday = dialog.findViewById(R.id.tv_wednesday);
                TextView tv_thursday = dialog.findViewById(R.id.tv_thursday);
                TextView tv_friday = dialog.findViewById(R.id.tv_friday);

                radioGroup = dialog.findViewById(R.id.radioGroup);

                // Spinner for end_point ( room )
                Spinner roomSpinner = dialog.findViewById(R.id.spinner_room_choice);
                ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item, roomList);
                spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                roomSpinner.setAdapter(spinnerArrayAdapter);
                roomSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        end_point = (String) roomSpinner.getItemAtPosition(position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        end_point = "none";
                        Log.i("GTO", "Nothing Selected");
                    }
                });

                // Spinner for end_point ( worker ) + showing additional informations
                Spinner workerSpinner = dialog.findViewById(R.id.spinner_worker_choice);
                ArrayAdapter<String> spinnerArrayAdapter2 = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_spinner_item, workers_names);
                spinnerArrayAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                workerSpinner.setAdapter(spinnerArrayAdapter2);
                workerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        worker = (String) workerSpinner.getItemAtPosition(position);
                        findWorkerId(worker);
                        tv_phone_no.setVisibility(View.VISIBLE);
                        tv_phone_no.setText(workers_list.get(index_-1).phone_no);
                        tv_worker.setVisibility(View.VISIBLE);
                        tv_worker.setText(workers_list.get(index_-1).degree + " " +workers_list.get(index_-1).name_ + " " + workers_list.get(index_-1).surename);
                        for(int i=0; i< workers_list.get(index_-1).consultations.size();i++)
                        {
                            if(i==0)
                            {
                                tv_monday.setText(workers_list.get(index_-1).consultations.get(i).day + " " + workers_list.get(index_-1).consultations.get(i).hours);
                                tv_monday.setVisibility(View.VISIBLE);
                            }
                            if(i==1)
                            {
                                tv_tuesday.setText(workers_list.get(index_-1).consultations.get(i).day + " " + workers_list.get(index_-1).consultations.get(i).hours);
                                tv_tuesday.setVisibility(View.VISIBLE);
                            }
                            if(i==2)
                            {
                                tv_wednesday.setText(workers_list.get(index_-1).consultations.get(i).day + " " + workers_list.get(index_-1).consultations.get(i).hours);
                                tv_wednesday.setVisibility(View.VISIBLE);
                            }
                            if(i==3)
                            {
                                tv_thursday.setText(workers_list.get(index_-1).consultations.get(i).day + " " + workers_list.get(index_-1).consultations.get(i).hours);
                                tv_thursday.setVisibility(View.VISIBLE);
                            }
                            if(i==4)
                            {
                                tv_friday.setText(workers_list.get(index_-1).consultations.get(i).day + " " + workers_list.get(index_-1).consultations.get(i).hours);
                                tv_friday.setVisibility(View.VISIBLE);
                            }
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        worker = "none";
                        Log.i("GTO", "Nothing Selected");
                    }
                });

                workerSpinner.setEnabled(false);
                workerSpinner.setVisibility(View.GONE);

                // Radio listener
                radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup radioGroup, int i){
                        if(i%2==0){
                            workerSpinner.setVisibility(View.GONE);
                            workerSpinner.setEnabled(false);
                            roomSpinner.setVisibility(View.VISIBLE);
                            roomSpinner.setEnabled(true);
                            type_of_point=false;

                            tv_monday.setVisibility(View.INVISIBLE);
                            tv_tuesday.setVisibility(View.INVISIBLE);
                            tv_wednesday.setVisibility(View.INVISIBLE);
                            tv_thursday.setVisibility(View.INVISIBLE);
                            tv_friday.setVisibility(View.INVISIBLE);
                            tv_worker.setVisibility(View.INVISIBLE);
                            tv_phone_no.setVisibility(View.INVISIBLE);
                        }
                        else{
                            workerSpinner.setVisibility(View.VISIBLE);
                            workerSpinner.setEnabled(true);
                            roomSpinner.setVisibility(View.GONE);
                            roomSpinner.setEnabled(false);
                            type_of_point=true;
                        }
                    }
                });

                // Confirm buttton of room choices
                okButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(path_to!=null) {
                            changeNodesPosition(getNodeBaseOnRoom(path_to));
                            if (type_of_point) {
                                end_point = getRoomBasedOnWorker(mainDataSnapshot.child("Room"));
                            }
                            nodes_after_search = AStar(getNodeBaseOnRoom(path_to), getNodeBaseOnRoom(end_point));
                            copyNodes();
                            seekbar.setVisibility(View.VISIBLE);
                            dialog.dismiss();
                        }
                        else {
                            Toast toast = Toast.makeText(context, "Nie zeskanowałeś QR kodu!", Toast.LENGTH_LONG);
                            toast.show();
                            dialog.dismiss();
                        }
                    }
                });
                dialog.show();
            }
        });

        ImageButton scanQR = findViewById(R.id.ib_scanqr);
        scanQR.setOnClickListener(
                (unusedView) -> {
                    scanBarcodes();
                });

        ImageButton help = findViewById(R.id.ib_help);
        help.setOnClickListener(
                (unusedView) -> {
                    openTutorial();
                });

        // Seekbar listener
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                calibrateAnchors(seekbar.getProgress());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        // Database stuff
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // D Database listener executes multiple times
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mainDataSnapshot = dataSnapshot;
                getNodes(dataSnapshot.child("Anchors"));
                setNeighbours();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

        // D Database listener executes once
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                roomList.add("none");
                getRoomNumbers(dataSnapshot.child("Room"));
                getWorkers(dataSnapshot.child("workers"));
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

        // Checks if the device is supported
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }


        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
        ModelRenderable.builder()
                .setSource(this, R.raw.sphere)
                .build()
                .thenAccept(renderable -> sphereRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load Renderable.", throwable);
                            return null;
                        });

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        // On touch stuff
        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (sphereRenderable == null) {
                        return;
                    }

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());
                });
    }

    // D Tutorial
    public void openTutorial(){
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.tutorial_ux);

        Button okbutton = dialog.findViewById(R.id.button);

        okbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    // Scanning QRCode
    private void scanBarcodes() {
        try {
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE"); // "PRODUCT_MODE for bar codes
            startActivityForResult(intent, 0);
        } catch (Exception e) {
            Uri marketUri = Uri.parse("market://details?id=com.google.zxing.client.android");
            Intent marketIntent = new Intent(Intent.ACTION_VIEW,marketUri);
            startActivity(marketIntent);
        }
    }

    // Results of QR scanning
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) path_to = data.getStringExtra("SCAN_RESULT");
            if(resultCode == RESULT_CANCELED){}
        }
    }

    // Finding worker id based on name
    private void findWorkerId(String name){
        for(int i=0;i<workers_names.size();i++)
        {
            if(workers_names.get(i)==name) index_=i+1;
        }
    }

    // D Find room based on worker id
    private String getRoomBasedOnWorker(DataSnapshot dataSnapshot){
        String room;
        for(DataSnapshot ds : dataSnapshot.getChildren())
        {
            if(ds.child(Integer.toString(index_)).exists()){
                room = ds.getKey();
                Log.d("FOUND",room);
                return room;
            }
        }
        return null;
    }

    // D Changing nodes X,Y,Z to adapt to exact location of astar
    private void changeNodesPosition(MyNode start_pos){
        Vector3 temp_vector = start_pos.getNode().getWorldPosition();
        Vector3 vector_fisnihed = new Vector3();
        float x,y,z;
        for(MyNode al : neighbours_nodes_list){
            x = temp_vector.x - al.getNode().getWorldPosition().x;
            y = temp_vector.y - al.getNode().getWorldPosition().y/2;
            z = temp_vector.z - al.getNode().getWorldPosition().z;
            vector_fisnihed.set(x,y,z);
            al.getNode().setWorldPosition(vector_fisnihed);
        }
    }

    // D Getting anchor position (x,y,z)
    private void getNodes(DataSnapshot dataSnapshot){
        double temp_x;
        double temp_y;
        double temp_z;
        Vector3 wektor3;

        for(DataSnapshot ds : dataSnapshot.getChildren()){
            MyNode myNode = new MyNode();

            ObjectConversion vars = ds.getValue(ObjectConversion.class);
            temp_x = vars.getX();
            temp_y = vars.getY();
            temp_z = vars.getZ();
            room_no = vars.getRoom();

            wektor3 = new Vector3((float)temp_x,(float)temp_y,(float)temp_z);
            Node aNode = new Node();

            aNode.setWorldPosition(wektor3);
            aNode.setParent(arFragment.getArSceneView().getScene());

            myNode.setNode(aNode);
            myNode.setRoom_no(room_no);

            neighbours_nodes_list.add(myNode);
        }
    }

    // D Creates copy of nodes, ready for rendering
    private void copyNodes(){
        Vector3 temp_vec;
        for(MyNode node : nodes_after_search){
            Node n = new Node();
            temp_vec = node.getNode().getWorldPosition();
            n.setWorldPosition(temp_vec);
            n.setParent(arFragment.getArSceneView().getScene());
            copied_nodes_list.add(n);
        }
    }

    // D Calibrate anchors to set them in real world
    private void calibrateAnchors(int alpha){
        Vector3 temp_vector;
        double rad = Math.toRadians(((alpha)%360)*Math.PI/180);
        if(alpha_before>alpha)
        {
            rad = Math.toRadians(-(((alpha)%360)*Math.PI/180));
        }
        alpha_before=alpha;
        for(Node al : copied_nodes_list){
            temp_vector = al.getWorldPosition();

            float newX = (float)(temp_vector.x * Math.cos(rad) - temp_vector.z * Math.sin(rad));
            float newZ = (float)(temp_vector.z * Math.cos(rad) + temp_vector.x * Math.sin(rad));

            temp_vector.x = newX;
            temp_vector.z = newZ;

            al.setWorldPosition(temp_vector);
            al.setParent(arFragment.getArSceneView().getScene());
            al.setRenderable(sphereRenderable);
        }
    }

    // D Find node based on room name
    private MyNode getNodeBaseOnRoom(String roomNumber) {
        String lookFor = roomNumber;
        for(MyNode an : neighbours_nodes_list){
            if(an.getRoom_no().equals(lookFor)) return an;
        }
        return null;
    }

    // D Setting neighoubrs for each node
    private void setNeighbours() {
        float xl,zl,yl;
        float xr,zr,yr;
        float distance;

        for(int i = 0; i < neighbours_nodes_list.size(); i++) {
            xl = neighbours_nodes_list.get(i).getNode().getWorldPosition().x;
            yl = neighbours_nodes_list.get(i).getNode().getWorldPosition().y;
            zl = neighbours_nodes_list.get(i).getNode().getWorldPosition().z;

            for(int j = 0; j < neighbours_nodes_list.size(); j++) {
                if(i==j) continue;

                xr = neighbours_nodes_list.get(j).getNode().getWorldPosition().x;
                yr = neighbours_nodes_list.get(j).getNode().getWorldPosition().y;
                zr = neighbours_nodes_list.get(j).getNode().getWorldPosition().z;

                distance = (float) (Math.sqrt(Math.pow((xl-xr),2) + Math.pow((yl-yr),2) + Math.pow((zl-zr),2)));

                if(distance < 1.5f) {
                   neighbours_nodes_list.get(i).addNeighbours(neighbours_nodes_list.get(j));
                }
            }
        }
    }

    // Euclidean distance between nodes
    private float countDistance(MyNode startNode, MyNode targetNode) {
        float xl,zl,yl,xr,zr,yr,distance;

        xl = startNode.getNode().getWorldPosition().x;
        yl = startNode.getNode().getWorldPosition().y;
        zl = startNode.getNode().getWorldPosition().z;

        xr = targetNode.getNode().getWorldPosition().x;
        yr = targetNode.getNode().getWorldPosition().y;
        zr = targetNode.getNode().getWorldPosition().z;

        distance = (float) (Math.sqrt(Math.pow((xl-xr),2) + Math.pow((yl-yr),2) + Math.pow((zl-zr),2)));
        return distance;
    }

    // Copy of euclidean distance with different parameters
    private float countDistanceOfNodes(Node startNode, Vector3 wektor) {
        float xl,zl,yl,xr,zr,yr,distance;

        xl = startNode.getWorldPosition().x;
        yl = startNode.getWorldPosition().y;
        zl = startNode.getWorldPosition().z;

        xr = wektor.x;
        yr = wektor.y;
        zr = wektor.z;

        distance = (float) (Math.sqrt(Math.pow((xl-xr),2) + Math.pow((yl-yr),2) + Math.pow((zl-zr),2)));
        return distance;
    }

    // D A* search alghoritm
    private List <MyNode> AStar(MyNode startNode, MyNode targetNode){
            List <MyNode> openList = new ArrayList<>();
            List <MyNode> closedList = new ArrayList<>();
            Map <MyNode,MyNode> cameFrom = new HashMap<>();
            MyNode currentNode;
            float costToConnection;

            openList.add(startNode);
            startNode.setgCost(0);
            startNode.sethCost(countDistance(startNode,targetNode));
            startNode.setfCost(startNode.gethCost());

            currentNode = startNode;

            while(openList.size()>0) {
                currentNode = openList.get(0);
                Log.d("ASTAR","WHILE");
                //Assinging current node to the node with lowest fScore
                for (int i = 0; i < openList.size(); i++) {
                    if (openList.get(i).getfCost() < currentNode.getfCost() ||
                            openList.get(i).getfCost() == currentNode.getfCost()
                                    && openList.get(i).gethCost() < currentNode.gethCost()) {
                        currentNode = openList.get(i);
                    }
                }

                //If found target node, return path
                if (currentNode == targetNode) {
                    Log.d("ASTAR", "ZNALAZLEM");
                    return reconstructPath(cameFrom, currentNode);
                }

                openList.remove(currentNode);
                closedList.add(currentNode);

                for (MyNode connection : currentNode.getNeighbours()) {
                    Log.d("ASTAR","FOR");
                    if (closedList.contains(connection)) continue;

                    costToConnection = currentNode.getgCost() + countDistance(currentNode, connection);

                    //Check if open list contains connection (neighbour)
                    if (!openList.contains(connection)) {
                        openList.add(connection);
                    } else if (costToConnection >= connection.getgCost()) continue;

                    cameFrom.put(connection, currentNode);
                    connection.setgCost(countDistance(startNode, connection));
                    connection.sethCost(countDistance(connection, targetNode));
                    connection.setfCost(connection.getgCost() + connection.gethCost());
                }
            }
            return null;
    }

    // D Needed for A* search
    public List <MyNode> reconstructPath(Map<MyNode,MyNode> cameFrom, MyNode current_node){
        List <MyNode> path = new ArrayList<>();
        path.add(current_node);
        for(MyNode current : cameFrom.keySet()){
            current = cameFrom.get(current);
            path.add(current);
        }
        //Collections.reverse(path);
        return path;
    }

    // D Getting room numbers and storing them in array once
    private void getRoomNumbers(DataSnapshot dataSnapshot){
        String temp_room;
        for(DataSnapshot ds : dataSnapshot.getChildren()){
            temp_room = ds.getKey();
            roomList.add(temp_room);
        }
    }

    // D Download list of workers
    private void getWorkers(DataSnapshot dataSnapshot){
        String temp_day,temp_time;

        for(DataSnapshot ds : dataSnapshot.getChildren()) {
            Workers worker = new Workers();
            worker.setDegree(ds.child("degree").getValue(String.class));
            worker.setName_(ds.child("name").getValue(String.class));
            worker.setSurename(ds.child("surename").getValue(String.class));
            worker.setPhone_no(ds.child("phone_no").getValue(String.class));
            for(DataSnapshot consultationsSnapshot : ds.child("consultations").getChildren()) {
                Consultations cons = new Consultations();
                temp_day = consultationsSnapshot.getKey();
                temp_time = consultationsSnapshot.getValue(String.class);
                cons.day = temp_day;
                cons.hours = temp_time;
                worker.consultations.add(cons);
            }
            workers_names.add(worker.getName_() + " " + worker.getSurename());
            workers_list.add(worker);
        }
    }

    // Getting next anchor id value
    private long get_Next_anchor_id(DataSnapshot dataSnapshot){
        long id;
        id = (long) dataSnapshot.child("Next_id").getValue();
        return id;
    }

    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * <p>Finishes the activity if Sceneform can not run
     */
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    // Realtime check to see if display or hide node's
    public void updateNodes()
    {
        Timer timer = new Timer();
        timer.schedule(new TimerTask(){
            int last = 0;
            int copy_last = 0;
            @Override
            public void run(){
                // We're checking all nodes for distance between them
                for(Node al : copied_nodes_list) {
                    if (last < copy_last) last++;
                    else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Camera camera = arFragment.getArSceneView().getScene().getCamera();
                                Vector3 camera_position = camera.getWorldPosition();

                                if (countDistanceOfNodes(al, camera_position) < 2.5 && copy_last >= last) {
                                    last++;
                                    copy_last = last;
                                    al.setEnabled(true);
                                } else {
                                    al.setEnabled(false);
                                }
                            }
                        });
                    }
                }
                last = 0;
            }
        },0,100);
    }

    // Realtime check to see if display or hide node's
    public void updateNodes1()
    {
        Camera camera = arFragment.getArSceneView().getScene().getCamera();
        Timer timer = new Timer();
        timer.schedule(new TimerTask(){


            float distance_start_to_finish=0.0f;

            @Override
            public void run(){
                Camera camera = arFragment.getArSceneView().getScene().getCamera();
                Vector3 camera_position = camera.getWorldPosition();

                // We're checking all nodes for distance between them
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                int last = 0;
                                int copy_last = 0;
                                int temp = 0;

                                for(int k=0;k<copied_nodes_list.size();k++) {
                                    if (last < copy_last){
                                        last++;
                                    }
                                    else {
                                        Camera camera = arFragment.getArSceneView().getScene().getCamera();
                                        Vector3 camera_position = camera.getWorldPosition();

                                        if (countDistanceOfNodes(copied_nodes_list.get(k), camera_position) < 2.0 && copy_last >= last) {
                                            last++;
                                            copy_last = last;
                                            temp = k;
                                            copied_nodes_list.get(k).setEnabled(true);

                                        } else {
                                            copied_nodes_list.get(k).setEnabled(false);
                                        }
                                        Log.d("INDEX: ", Integer.toString(temp));
                                        Log.d("SIZE: ", Integer.toString(copied_nodes_list.size()));
                                    }
                                }

                                for(int i=0;i<copied_nodes_list.size();i++)
                                {
                                    if(i==temp)
                                    {
                                        copied_nodes_list.get(i).setRenderable(sphereRenderable);
                                    }
                                    else copied_nodes_list.get(i).setRenderable(sphereRenderable);
                                }

                            }
                        });
            }
        },0,100);
    }

    // Have to be included for buttons to work
    @Override
    public void onClick(View view) {
    }
}