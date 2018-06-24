package com.example.pictionnaro.pictionnaro.Acitivities;

import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.pictionnaro.pictionnaro.Adapters.FirebaseListAdapter;
import com.example.pictionnaro.pictionnaro.Network.SyncedBoardManager;
import com.example.pictionnaro.pictionnaro.R;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class BoardListActivity extends AppCompatActivity {

    public static final String TAG = "AndroidDrawing";
    private static final int LOGOUT = 1;
    private static String FIREBASE_URL = "https://pictionnaro.firebaseio.com";
    static final int REQUEST_CODE = 42;

    private Button createGameButton;
    private String username;
    private AlertDialog mDialog;
    private Firebase mRef;
    private Firebase mBoardsRef;
    private Firebase mSegmentsRef;
    private FirebaseListAdapter<HashMap> mBoardListAdapter;
    private ValueEventListener mConnectedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRef = new Firebase(FIREBASE_URL);
        mBoardsRef = mRef.child("boardmetas");
        mBoardsRef.keepSynced(true); // keep the board list in sync
        mSegmentsRef = mRef.child("boardsegments");

        SyncedBoardManager.restoreSyncedBoards(mSegmentsRef);
        setContentView(R.layout.activity_board_list);
    }

    @Override
    protected void onStart() {
        super.onStart();

        createGameButton = (Button) findViewById(R.id.create_game_button);
        createGameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showGameCreationDialog();
            }
        });

        // Set up a notification to let us know when we're connected or disconnected from the Firebase servers
        mConnectedListener = mRef.getRoot().child(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                boolean connected = (Boolean) dataSnapshot.getValue();
                if (connected) {
                    Toast.makeText(BoardListActivity.this, "Connected to Firebase", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(BoardListActivity.this, "Disconnected from Firebase", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                // No-op
            }
        });

        final ListView boardList = (ListView) this.findViewById(R.id.BoardList);
        mBoardListAdapter = new FirebaseListAdapter<HashMap>(mBoardsRef, HashMap.class, R.layout.board_in_list, this) {
            @Override
            protected void populateView(View v, HashMap model) {
                final String key = BoardListActivity.this.mBoardListAdapter.getModelKey(model);

                // To fill game name textview and creation date textview inside list element
                if (model.get("gameName") != null) {
                    ((TextView) v.findViewById(R.id.tv_board_title)).setText(model.get("gameName").toString());
                }
                if (model.get("createdAt") != null) {
                    ((TextView) v.findViewById(R.id.tv_creation_date)).setText(model.get("createdAt").toString());
                }
                if (model.get("createdByUName") != null) {
                    ((TextView) v.findViewById(R.id.tv_board_creator)).setText(model.get("createdByUName").toString());
                }
                // show if the board is synced and listen for clicks to toggle that state
                /*CheckBox checkbox = (CheckBox) v.findViewById(R.id.keepSynced);
                checkbox.setChecked(SyncedBoardManager.isSynced(key));
                checkbox.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SyncedBoardManager.toggle(mSegmentsRef, key);
                    }
                });*/

                // display the board's thumbnail if it is available
                ImageView thumbnailView = (ImageView) v.findViewById(R.id.board_thumbnail);
                if (model.get("thumbnail") != null){
                    try {
                        thumbnailView.setImageBitmap(DrawingActivity.decodeFromBase64(model.get("thumbnail").toString()));
                        thumbnailView.setVisibility(View.VISIBLE);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    thumbnailView.setVisibility(View.INVISIBLE);
                }
            }
        };
        boardList.setAdapter(mBoardListAdapter);
        boardList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openBoard(mBoardListAdapter.getModelKey(position));
            }
        });
        mBoardListAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                boardList.setSelection(mBoardListAdapter.getCount() - 1);
            }
        });
        mRef.child("users").child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child("username").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                username = dataSnapshot.getValue().toString();
            }
            @Override
            public void onCancelled(FirebaseError firebaseError) {
            }
        });
        getSupportActionBar().setTitle("LOBBY");
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Clean up our listener so we don't have it attached twice.
        mRef.getRoot().child(".info/connected").removeEventListener(mConnectedListener);
        mBoardListAdapter.cleanup();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // getMenuInflater().inflate(R.menu.menu_drawing, menu);

        menu.add(0, LOGOUT, 0, "Logout").setShortcut('3', 'c').setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == LOGOUT) {
            finish();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        return;
    }

    private void showGameCreationDialog() {
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(BoardListActivity.this);
        View mView = getLayoutInflater().inflate(R.layout.dialog_game_creation, null);
        final EditText mGameName = (EditText) mView.findViewById(R.id.et_game_name);
        final EditText mGameWord = (EditText) mView.findViewById(R.id.et_game_word);
        final Button mCreateGame = (Button) mView.findViewById(R.id.button_create_game);
        final TextView mInvalidGameName = (TextView) mView.findViewById(R.id.tv_bad_game_name);
        final TextView mInvalidGameWord = (TextView) mView.findViewById(R.id.tv_bad_game_word);
        final CheckBox mShowWord = (CheckBox) mView.findViewById(R.id.cb_show_word_to_find);
        final CheckBox mDrawPermission = (CheckBox) mView.findViewById(R.id.cb_draw_permission);
        final View mProgress = (View) mView.findViewById(R.id.creation_progress);

        mShowWord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mShowWord.isChecked()) {
                    mGameWord.setTransformationMethod(null);
                } else {
                    mGameWord.setTransformationMethod(new PasswordTransformationMethod());
                }
            }
        });
        mCreateGame.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mGameName.getText().toString().isEmpty()) {
                    mInvalidGameName.setVisibility(View.VISIBLE);
                }
                if (mGameWord.getText().toString().isEmpty()) {
                    mInvalidGameWord.setVisibility(View.VISIBLE);
                }
                if (!mGameName.getText().toString().isEmpty() && !mGameWord.getText().toString().isEmpty()) {
                    createBoard(mGameName.getText().toString(), mGameWord.getText().toString(), mDrawPermission.isChecked());
                    //to create only one game at a time
                    mCreateGame.setEnabled(false);
                    mProgress.setVisibility(View.VISIBLE);
                }
            }
        });
        mBuilder.setView(mView);
        mDialog = mBuilder.create();
        mDialog.show();
    }

    // This function create a new board in database
    private void createBoard(String gameName, String gameWord, final boolean drawPermissionForAll) {
        // First create board reference (a key stored in Firebase DataBase)
        final Firebase newBoardRef = mBoardsRef.push();
        // Then create board values
        Map<String, Object> newBoardValues = new HashMap<>();
        FirebaseUser currentFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        Date date = new Date();
        String currentDay = new SimpleDateFormat("hh:mm a   dd/MM/yyyy").format(date);

        newBoardValues.put("createdByID", currentFirebaseUser.getUid());
        newBoardValues.put("createdByUName", username);
        newBoardValues.put("createdAt", currentDay);
        newBoardValues.put("drawPermissionForAll", drawPermissionForAll);
        newBoardValues.put("gameName", gameName);
        newBoardValues.put("wordToFind", gameWord);
        newBoardValues.put("winner", "");


        android.graphics.Point size = new android.graphics.Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        newBoardValues.put("width", size.x);
        newBoardValues.put("height", size.y);
        // Finally set values to board reference
        newBoardRef.setValue(newBoardValues, new Firebase.CompletionListener() {
            @Override
            public void onComplete(FirebaseError firebaseError, Firebase ref) {
                if (firebaseError != null) {
                    Log.e(TAG, firebaseError.toString());
                    throw firebaseError.toException();
                } else {
                    // once the board is created, start a DrawingActivity on it
                    Log.e("TEST", "CREATION UID =" + FirebaseAuth.getInstance().getCurrentUser().getUid());
                    openBoard(newBoardRef.getKey());
                }
            }
        });
    }

    private void openBoard(String key) {
        Log.i(TAG, "Opening board "+key);
        Toast.makeText(BoardListActivity.this, "Opening board: "+key, Toast.LENGTH_LONG).show();
        if (mDialog != null) {
            mDialog.cancel();
        }
        Intent intent = new Intent(this, DrawingActivity.class);
        intent.putExtra("FIREBASE_URL", FIREBASE_URL);
        intent.putExtra("BOARD_ID", key);
        startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String boardId = data.getStringExtra("boardId");
                /*mRef.child("boardmetas").child(boardId).removeValue();
                mRef.child("boardsegments").child(boardId).removeValue();*/
                //mRef.updateChildren();
                mBoardListAdapter.notifyDataSetChanged();
                //mSegmentsRef = mFirebaseRef.child("boardsegments").child(mBoardId);
            }
        }
    }
}
