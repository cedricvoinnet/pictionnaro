package com.example.pictionnaro.pictionnaro.Acitivities;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.pictionnaro.pictionnaro.Dialogs.ChatGameDialog;
import com.example.pictionnaro.pictionnaro.Dialogs.ColorPickerDialog;
import com.example.pictionnaro.pictionnaro.Models.Segment;
import com.example.pictionnaro.pictionnaro.Network.SyncedBoardManager;
import com.example.pictionnaro.pictionnaro.R;
import com.example.pictionnaro.pictionnaro.Views.DrawingView;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public class DrawingActivity extends AppCompatActivity implements ColorPickerDialog.OnColorChangedListener
 {
     public static final int THUMBNAIL_SIZE = 350;

     private static final int COLOR_MENU_ID = Menu.FIRST;
     private static final int CLEAR_MENU_ID = COLOR_MENU_ID + 1;
     private static final int PIN_MENU_ID = CLEAR_MENU_ID + 1;
     private static final int END_GAME_MENU_ID = PIN_MENU_ID + 1;
     private static final int CHAT_MENU_ID = END_GAME_MENU_ID + 1;
     public static final String TAG = "AndroidDrawing";

     private boolean shouldUpdateThmbnail = true;
     private boolean isCreator = false;
     private boolean drawPermissionForAll;
     private boolean gameIsOver = false;
     private boolean firstTimeGetMessage = true;
     private int mBoardWidth;
     private int mBoardHeight;
     private int unreadMessage;
     private String mBoardId;
     private String wordToFind;
     private DrawingView mDrawingView = null;
     private AlertDialog dialog = null;
     private Firebase mFirebaseRef;
     private Firebase mMetadataRef;
     private Firebase mSegmentsRef;
     private Firebase mChatRef;
     private ValueEventListener mMetaRefListener;
     private FirebaseUser currentFirebaseUser;
     private ValueEventListener mConnectedListener;
     private TextView textChatItemCount;
     private Dialog chatGameDialog;


     @Override
     public void onCreate(Bundle savedInstanceState) {
         super.onCreate(savedInstanceState);
         Intent intent = getIntent();
         final String url = intent.getStringExtra("FIREBASE_URL");
         final String boardId = intent.getStringExtra("BOARD_ID");

         currentFirebaseUser = FirebaseAuth.getInstance().getCurrentUser();
         mFirebaseRef = new Firebase(url);
         mBoardId = boardId;
         chatGameDialog = new ChatGameDialog(this, mFirebaseRef, mBoardId, this);
         initFireBaseRefs();
     }

     @Override
     public void onStart() {
         super.onStart();
         // Set up a notification to let us know when we're connected or disconnected from the Firebase servers
         mConnectedListener = mFirebaseRef.getRoot().child(".info/connected").addValueEventListener(new ValueEventListener() {
             @Override
             public void onDataChange(DataSnapshot dataSnapshot) {
                 boolean connected = (Boolean) dataSnapshot.getValue();
                 if (connected) {
                     //Toast.makeText(DrawingActivity.this, "Connected to Firebase", Toast.LENGTH_SHORT).show();
                     Log.e("drawingAct", "connected to firebase");
                 } else {
                     Toast.makeText(DrawingActivity.this, "Disconnected from Firebase", Toast.LENGTH_SHORT).show();
                 }
             }

             @Override
             public void onCancelled(FirebaseError firebaseError) {
                 // No-op
             }
         });
     }

     @Override
     public void onStop() {
         super.onStop();
         // Clean up our listener so we don't have it attached twice.
         mFirebaseRef.getRoot().child(".info/connected").removeEventListener(mConnectedListener);
         if (mDrawingView != null) {
             mDrawingView.cleanup();
         }
         mFirebaseRef.removeEventListener(mMetaRefListener);
         if (dialog != null && dialog.isShowing()) {
             dialog.dismiss();
         }
         if (shouldUpdateThmbnail) {
             this.updateThumbnail(mBoardWidth, mBoardHeight, mSegmentsRef, mMetadataRef);
         }
     }

     @Override
     public void onPause() {
         super.onPause();
     }

     @Override
     public boolean onCreateOptionsMenu(Menu menu) {
         //add menu basics options
         if (isCreator/* || (mDrawingView != null && mDrawingView.isDrawable())*/) {
             menu.add(0, CLEAR_MENU_ID, 2, "Clear").setShortcut('5', 'x');
             menu.add(0, END_GAME_MENU_ID, 4, "end the game");
         } else if (drawPermissionForAll) {
             menu.add(0, CLEAR_MENU_ID, 2, "Clear").setShortcut('5', 'x');
         }

         //add menu chatRoom and get notifications
         getMenuInflater().inflate(R.menu.menu_board_room, menu);
         final MenuItem menuChatItem = menu.findItem(R.id.action_chat);
         View chatActionView = MenuItemCompat.getActionView(menuChatItem);
         textChatItemCount = (TextView) chatActionView.findViewById(R.id.chat_notification);
         chatActionView.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 onOptionsItemSelected(menuChatItem);
             }
         });
         updateNotification();

         //if user is not the creator of the game or is drawForAll permission is disable, we have to hide colorPicker Menu
         if (!(isCreator || drawPermissionForAll)) {
             final MenuItem menuColorItem = menu.findItem(R.id.action_color);
             menuColorItem.setVisible(false);
         }

         return true;
     }

     @Override
     public boolean onPrepareOptionsMenu(Menu menu) {
         super.onPrepareOptionsMenu(menu);
         return true;
     }

     @Override
     public boolean onOptionsItemSelected(MenuItem item) {
         if (item.getItemId() == COLOR_MENU_ID || item.getItemId() == R.id.action_color) {
             new ColorPickerDialog(this, this, 0xFFFF0000).show();
             return true;
         } else if (item.getItemId() == CLEAR_MENU_ID) {
             mDrawingView.cleanup();
             mSegmentsRef.removeValue(new Firebase.CompletionListener() {
                 @Override
                 public void onComplete(FirebaseError firebaseError, Firebase firebase) {
                     if (firebaseError != null) {
                         throw firebaseError.toException();
                     }
                     mDrawingView = new DrawingView(DrawingActivity.this, mFirebaseRef.child("boardsegments").child(mBoardId), mBoardWidth, mBoardHeight);
                     setContentView(mDrawingView);
                     updateThumbnail(mBoardWidth, mBoardHeight, mFirebaseRef.child("boardsegments").child(mBoardId), mFirebaseRef.child("boardmetas").child(mBoardId));
                 }
             });
             return true;
         } else if (item.getItemId() == PIN_MENU_ID) {
             SyncedBoardManager.toggle(mFirebaseRef.child("boardsegments"), mBoardId);
             item.setChecked(SyncedBoardManager.isSynced(mBoardId));
             return true;
         } else if (item.getItemId() == END_GAME_MENU_ID) {
             removeGame();
             return true;
         } else if (item.getItemId() == R.id.action_chat) {
             chatGameDialog.show();
             unreadMessage = 0;
             updateNotification();
             return true;
         } else {
             return super.onOptionsItemSelected(item);
         }
     }

     private void initFireBaseRefs() {
         mMetadataRef = mFirebaseRef.child("boardmetas").child(mBoardId);
         mSegmentsRef = mFirebaseRef.child("boardsegments").child(mBoardId);
         mChatRef = mFirebaseRef.child("boardchat").child(mBoardId);
         mMetaRefListener = new ValueEventListener() {
             @Override
             public void onDataChange(DataSnapshot dataSnapshot) {
                 if (dataSnapshot.exists()) {
                     if (mDrawingView != null) {
                         ((ViewGroup) mDrawingView.getParent()).removeView(mDrawingView);
                         mDrawingView.cleanup();
                         mDrawingView = null;
                     }
                     Map<String, Object> boardValues = (Map<String, Object>) dataSnapshot.getValue();
                     if (boardValues != null && boardValues.get("width") != null && boardValues.get("height") != null) {
                         mBoardWidth = ((Long) boardValues.get("width")).intValue();
                         mBoardHeight = ((Long) boardValues.get("height")).intValue();
                         wordToFind = ((String) boardValues.get("wordToFind")).toString();

                         mDrawingView = new DrawingView(DrawingActivity.this, mSegmentsRef, mBoardWidth, mBoardHeight);
                         // Draw permission only for the user game-creator
                         getSupportActionBar().setTitle("CREATOR ONLY");
                         if (((Map<String, Object>) dataSnapshot.getValue()).get("createdByID").equals(currentFirebaseUser.getUid())) {
                             isCreator = true;
                             mDrawingView.setIsDrawable(true);
                         } else {
                             mDrawingView.setIsDrawable(false);
                         }
                         if (boardValues.get("drawPermissionForAll").equals(true)) {
                             drawPermissionForAll = true;
                             mDrawingView.setIsDrawable(true);
                             getSupportActionBar().setTitle("DRAW FOR ALL!");
                         }
                         invalidateOptionsMenu();
                         setContentView(mDrawingView);
                         if (boardValues.get("winner").toString() != "") {
                             showBackToGameList(boardValues.get("winner").toString());
                             removeGame();
                             gameIsOver = true;
                         }
                     }
                 } else if (!gameIsOver) {
                     showBackToGameList("nobody won");
                 }
             }

             @Override
             public void onCancelled(FirebaseError firebaseError) {
             }
         };
         mMetadataRef.addValueEventListener(mMetaRefListener);

         mSegmentsRef.addValueEventListener(new ValueEventListener() {
             @Override
             public void onDataChange(DataSnapshot dataSnapshot) {
                 if (dataSnapshot.exists()) {
                    mDrawingView.invalidate();
                 } else {
                     mDrawingView.clearDraw();
                     mDrawingView.invalidate();
                 }
             }

             @Override
             public void onCancelled(FirebaseError firebaseError) {
             }
         });

         mChatRef.addValueEventListener(new ValueEventListener() {
             @Override
             public void onDataChange(DataSnapshot dataSnapshot) {
                 if (firstTimeGetMessage) {
                     Map<String, Object> messageValues = (Map<String, Object>) dataSnapshot.getValue();
                     if (messageValues != null) {
                         unreadMessage = messageValues.size();
                         updateNotification();
                     }
                     firstTimeGetMessage = false;
                 } else if (!chatGameDialog.isShowing()) {
                     unreadMessage += 1;
                     updateNotification();
                 }
             }

             @Override
             public void onCancelled(FirebaseError firebaseError) {
             }
         });
     }

     private void removeGame() {
         mMetadataRef.removeValue();
         mSegmentsRef.removeValue();
         mChatRef.removeValue();
     }

     private void showBackToGameList(String gameWinner) {
         AlertDialog.Builder mBuilder = new AlertDialog.Builder(DrawingActivity.this);
         View mView = getLayoutInflater().inflate(R.layout.dialog_end_game, null);
         final Button mLeaveGame = (Button) mView.findViewById(R.id.button_back);
         TextView tvWordToFind = (TextView) mView.findViewById(R.id.tv_word_to_find);
         TextView tvGameWinnerName = (TextView) mView.findViewById(R.id.tv_game_winner_name);

         mBuilder.setView(mView);
         dialog = mBuilder.create();
         dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
             @Override
             public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                 if (keyCode == KeyEvent.KEYCODE_BACK) {
                 if (dialog != null) {
                     dialog.dismiss();
                     shouldUpdateThmbnail = false;
                     finish();
                     }
                 }
                 return true;
             }
         });
         dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
             @Override
             public void onDismiss(DialogInterface dialog) {
                 shouldUpdateThmbnail = false;
                 finish();
             }
         });
         mLeaveGame.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) {
                 if (dialog != null) {
                     dialog.dismiss();
                     shouldUpdateThmbnail = false;
                     finish();
                 }
             }
         });
         tvWordToFind.setText(wordToFind);
         tvGameWinnerName.setText(gameWinner);
         if (!isFinishing()) {
             dialog.show();
         }
         //}
     }

     public static void updateThumbnail(int boardWidth, int boardHeight, Firebase segmentsRef, final Firebase metadataRef) {
         if (boardHeight > 0 && boardHeight > 0) {
             final float scale = Math.min(1.0f * THUMBNAIL_SIZE / boardWidth, 1.0f * THUMBNAIL_SIZE / boardHeight);
             final Bitmap b = Bitmap.createBitmap(Math.round(boardWidth * scale), Math.round(boardHeight * scale), Bitmap.Config.ARGB_8888);
             final Canvas buffer = new Canvas(b);

             buffer.drawRect(0, 0, b.getWidth(), b.getHeight(), DrawingView.paintFromColor(Color.WHITE, Paint.Style.FILL_AND_STROKE));

             segmentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                 @Override
                 public void onDataChange(DataSnapshot dataSnapshot) {
                     for (DataSnapshot segmentSnapshot : dataSnapshot.getChildren()) {
                         Segment segment = segmentSnapshot.getValue(Segment.class);
                         buffer.drawPath(
                                 DrawingView.getPathForPoints(segment.getPoints(), scale),
                                 DrawingView.paintFromColor(segment.getColor())
                         );
                     }
                     String encoded = encodeToBase64(b);
                     metadataRef.child("thumbnail").setValue(encoded, new Firebase.CompletionListener() {
                         @Override
                         public void onComplete(FirebaseError firebaseError, Firebase firebase) {
                             if (firebaseError != null) {
                                 Log.e(TAG, "Error updating thumbnail", firebaseError.toException());
                             }
                         }
                     });
                 }

                 @Override
                 public void onCancelled(FirebaseError firebaseError) {

                 }
             });
         }
     }

     private void updateNotification() {

         if (textChatItemCount != null) {
             if (unreadMessage == 0) {
                 if (textChatItemCount.getVisibility() != View.GONE) {
                     textChatItemCount.setVisibility(View.GONE);
                 }
             } else {
                 textChatItemCount.setText(String.valueOf(Math.min(unreadMessage, 99)));
                 if (textChatItemCount.getVisibility() != View.VISIBLE) {
                     textChatItemCount.setVisibility(View.VISIBLE);
                 }
             }
         }
     }

     public static String encodeToBase64(Bitmap image) {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         image.compress(Bitmap.CompressFormat.PNG, 100, baos);
         byte[] b = baos.toByteArray();
         String imageEncoded = com.firebase.client.utilities.Base64.encodeBytes(b);

         return imageEncoded;
     }
     public static Bitmap decodeFromBase64(String input) throws IOException {
         byte[] decodedByte = com.firebase.client.utilities.Base64.decode(input);
         return BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length);
     }

     @Override
     public void colorChanged(int newColor) {
         mDrawingView.setColor(newColor);
     }

 }
