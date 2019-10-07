package com.navigation.navinhere;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.support.v4.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;

public class ArNavActivity extends FragmentActivity implements View.OnClickListener {
    private static final String TAG = ArNavActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;
    final Context context = this;

    // ARcore stuff
    private ArFragment arFragment;
    private ModelRenderable sphereRenderable;

    //Anchors stuff
    private double tx, ty, tz;
    private long anchor_id = 0;
    private long next_anchor_id=0;
    private long worker_id=0;
    private long next_worker_id=0;

    private String room_no = "";
    private String day1,day2,day3,day4;
    private int pos = 0;

    //Database stuff
    private DatabaseReference mDatabase;
    private List <String> roomList = new ArrayList<>();
    private List <String> daysList = new ArrayList<>();
    private List <Workers> workers_list = new ArrayList<>();
    private List <String> workers_names = new ArrayList<>();


    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})

    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ux);

        daysList.add("brak");
        daysList.add("Poniedziałek");
        daysList.add("Wtorek");
        daysList.add("Środa");
        daysList.add("Czwartek");
        daysList.add("Piątek");
        daysList.add("Sobota");
        daysList.add("Niedziela");

        openTutorial();

        // Database stuff
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Database listener executes multiple times
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                next_anchor_id = get_Next_anchor_id(dataSnapshot);
                next_worker_id = get_Next_worker_id(dataSnapshot);
                getRoomNumbers(dataSnapshot.child("Room"));
                getWorkers(dataSnapshot.child("workers"));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

        // Database listener executes once
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                roomList.add("none");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        // Checking if device is supported ( android requirement + openGL support)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        ImageButton add_workers = findViewById(R.id.butt_add_worker);
        add_workers.setOnClickListener(
                (unusedView) -> {
                    openWorkersDialog();
                });

        ImageButton connect_workers = findViewById(R.id.butt_add_room);
        connect_workers.setOnClickListener(
                (unusedView) -> {
                    openRoomDialog();
                });

        ImageButton help = findViewById(R.id.help);
        help.setOnClickListener(
                (unusedView) -> {
                    openTutorial();
                });

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
        // Creating nav points
        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());
                    anchorNode.setRenderable(sphereRenderable);
                    ty = anchor.getPose().ty();
                    tx = anchor.getPose().tx();
                    tz = anchor.getPose().tz();
                    anchor_id = next_anchor_id;
                    openAnchorsDialog();
                });
    }

    // Dialog for uploading anchors with rooms
    public void openAnchorsDialog() {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_anchor_ux);

        TextView text =  dialog.findViewById(R.id.text);
        text.setText("Wybierz rodzaj punktu bądź salę, lub dodaj nowy punkt startowy i przypisz go do punktu.");

        Button dialogButton = dialog.findViewById(R.id.dialogButtonOK);
        Button cancelButton = dialog.findViewById(R.id.dialogButtonDiscard);
        Button addButton = dialog.findViewById(R.id.bt_add);

        EditText et_room = dialog.findViewById(R.id.et_room_dialog);

        Spinner spinner = dialog.findViewById(R.id.spinner_room);
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,roomList);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerArrayAdapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                room_no= (String) spinner.getItemAtPosition(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                room_no="none";
                Log.i("GTO", "Nothing Selected");
            }
        });

        // Adding new starting point / room
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDatabase.child("Room").child(et_room.getText().toString()).setValue("");
            }
        });

        // Uploading point to database
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDatabase.child("Next_id").setValue(next_anchor_id+1);
                mDatabase.child("Anchors").child(Long.toString(anchor_id)).child("x").setValue(tx);
                mDatabase.child("Anchors").child(Long.toString(anchor_id)).child("y").setValue(ty);
                mDatabase.child("Anchors").child(Long.toString(anchor_id)).child("z").setValue(tz);
                mDatabase.child("Anchors").child(Long.toString(anchor_id)).child("room").setValue(room_no);
                dialog.dismiss();
            }
        });

        // Cancel button
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    // Dialog for adding workers
    public void openWorkersDialog() {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_worker_ux);

        TextView text =  dialog.findViewById(R.id.text);
        text.setText("Uzupełnij dane o pracowniku aby go dodać.");
        Button okButton = dialog.findViewById(R.id.dialogButtonOK);
        Button cancelButton = dialog.findViewById(R.id.dialogButtonDiscard);

        Spinner spinner1 = dialog.findViewById(R.id.sp_day1);
        Spinner spinner2 = dialog.findViewById(R.id.sp_day2);
        Spinner spinner3 = dialog.findViewById(R.id.sp_day3);
        Spinner spinner4 = dialog.findViewById(R.id.sp_day4);

        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,daysList);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner1.setAdapter(spinnerArrayAdapter);
        spinner2.setAdapter(spinnerArrayAdapter);
        spinner3.setAdapter(spinnerArrayAdapter);
        spinner4.setAdapter(spinnerArrayAdapter);

        spinner1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                day1= (String) spinner1.getItemAtPosition(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                day1="brak";
                Log.i("GTO", "Nothing Selected");
            }
        });
        spinner2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                day2= (String) spinner1.getItemAtPosition(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                day2="brak";
                Log.i("GTO", "Nothing Selected");
            }
        });
        spinner3.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                day3= (String) spinner1.getItemAtPosition(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                day3="brak";
                Log.i("GTO", "Nothing Selected");
            }
        });
        spinner4.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                day4= (String) spinner1.getItemAtPosition(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                day4="brak";
                Log.i("GTO", "Nothing Selected");
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            EditText et;
            String content;
            @Override
            public void onClick(View v) {
                worker_id = next_worker_id;
                mDatabase.child("Next_worker_id").setValue(next_worker_id+1);

                et = dialog.findViewById(R.id.et_name);
                content = et.getText().toString();
                mDatabase.child("workers").child(Long.toString(worker_id)).child("name").setValue(content);

                et = dialog.findViewById(R.id.et_surename);
                content = et.getText().toString();
                mDatabase.child("workers").child(Long.toString(worker_id)).child("surename").setValue(content);

                et = dialog.findViewById(R.id.et_degree);
                content = et.getText().toString();
                mDatabase.child("workers").child(Long.toString(worker_id)).child("degree").setValue(content);

                et = dialog.findViewById(R.id.et_number);
                content = et.getText().toString();
                mDatabase.child("workers").child(Long.toString(worker_id)).child("phone_no").setValue(content);

                if(!day1.equals("brak"))
                {
                    et = dialog.findViewById(R.id.et_hour1);
                    content = et.getText().toString();
                    mDatabase.child("workers").child(Long.toString(worker_id)).child("consultations").child(day1).setValue(content);
                }
                if(!day2.equals("brak"))
                {
                    et = dialog.findViewById(R.id.et_hour2);
                    content = et.getText().toString();
                    mDatabase.child("workers").child(Long.toString(worker_id)).child("consultations").child(day2).setValue(content);
                }
                if(!day3.equals("brak"))
                {
                    et = dialog.findViewById(R.id.et_hour3);
                    content = et.getText().toString();
                    mDatabase.child("workers").child(Long.toString(worker_id)).child("consultations").child(day3).setValue(content);
                }
                if(!day4.equals("brak"))
                {
                    et = dialog.findViewById(R.id.et_hour1);
                    content = et.getText().toString();
                    mDatabase.child("workers").child(Long.toString(worker_id)).child("consultations").child(day4).setValue(content);
                }

                dialog.dismiss();
            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    // Dialog made for assigning workers to rooms
    public void openRoomDialog() {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.dialog_room_ux);

        TextView text =  dialog.findViewById(R.id.text);
        text.setText("Wybierz numer sali lub dodaj nową, a następnie przypisz do niej pracownika.");

        Button dialogButton = dialog.findViewById(R.id.dialogButtonOK);
        Button cancelButton = dialog.findViewById(R.id.dialogButtonDiscard);
        Button addButton = dialog.findViewById(R.id.dialogButtonAdd);

        Spinner sp_rooms = dialog.findViewById(R.id.sp_room);
        Spinner sp_worker = dialog.findViewById(R.id.sp_worker);
        EditText room_name = dialog.findViewById(R.id.et_room);

        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,roomList);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ArrayAdapter<String> spinnerArrayAdapter2 = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,workers_names);
        spinnerArrayAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        sp_rooms.setAdapter(spinnerArrayAdapter);
        sp_worker.setAdapter(spinnerArrayAdapter2);

        // Spinner for obtaining value for room
        sp_rooms.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                room_no= (String) sp_rooms.getItemAtPosition(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                room_no="none";
                Log.i("GTO", "Nothing Selected");
            }
        });

        // Spinner for obtaining value for worker
        sp_worker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    pos=position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                    pos=1;
                Log.i("GTO", "Nothing Selected");
            }
        });

        //Accept button
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDatabase.child("Room").child(room_no).child(Integer.toString(pos+1)).setValue("");
                dialog.dismiss();
            }
        });

        // Button for adding room
        addButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                    if(room_name.length()!=0) {
                        mDatabase.child("Room").child(room_name.getText().toString()).setValue("none");
                    }
                    else {
                        int duration = Toast.LENGTH_SHORT;
                        Context context = getApplicationContext();
                        Toast toast = Toast.makeText(context,"Nie wpisano numeru sali!",duration);
                        toast.show();
                    }
            }
        });

        //Dismiss button
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    // Tutorial
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

    // Getting room numbers and storing them in array once
    private void getRoomNumbers(DataSnapshot dataSnapshot){
        String temp_room;
        roomList.clear();
        for(DataSnapshot ds : dataSnapshot.getChildren()){
            temp_room = ds.getKey();
            roomList.add(temp_room);
        }
    }

    // Download list of workers
    private void getWorkers(DataSnapshot dataSnapshot){
        String temp_day,temp_time;
        workers_list.clear();
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

    // Getting next anchor id value
    private long get_Next_worker_id(DataSnapshot dataSnapshot){
        long id;
        id = (long) dataSnapshot.child("Next_worker_id").getValue();
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

    // Have to be included for buttons to work
    @Override
    public void onClick(View view) {

    }
}