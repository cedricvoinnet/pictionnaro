package com.example.pictionnaro.pictionnaro.Dialogs;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.example.pictionnaro.pictionnaro.Adapters.FirebaseListAdapter;
import com.example.pictionnaro.pictionnaro.Models.ChatMessage;
import com.example.pictionnaro.pictionnaro.R;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Map;

/**
 * Created by jordan on 07/11/17.
 */

public class ChatGameDialog extends Dialog {

    private String username;
    private String creatorID;
    private ListView listOfMessages;
    private Context mContext;
    private EditText input;
    private Firebase mUserRef;
    private Firebase mChatRef;
    private Firebase mMetaRef;
    private Activity mCallingActivity;
    private FloatingActionButton mFab;
    private FirebaseListAdapter<ChatMessage> adapter;

    public ChatGameDialog(@NonNull Context context, Firebase dbRef, String boardID, Activity callingActivity) {
        super(context);
        mContext = context;
        mUserRef = dbRef.child("users");
        mChatRef = dbRef.child("boardchat").child(boardID);
        mMetaRef = dbRef.child("boardmetas").child(boardID);
        mCallingActivity = callingActivity;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.dialog_game_chat);
        initView();
    }

    private void initView() {
        mFab = (FloatingActionButton) findViewById(R.id.fab);
        input = (EditText)findViewById(R.id.input);
        mMetaRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                Map<String, Object> boardValues = (Map<String, Object>) dataSnapshot.getValue();
                if (boardValues != null) {
                    creatorID = boardValues.get("createdByID").toString();
                    //Log.e("TEST", "id =" + boardValues.get("createdByID"));
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {

            }
        });
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Get username value from firebase by userID
                if (input.getText().length() > 0) {
                    mUserRef.child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child("username").addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            username = dataSnapshot.getValue().toString();
                            // Read the input field and push a new instance
                            // of ChatMessage to the Firebase database
                            mChatRef.push().setValue(new ChatMessage(input.getText().toString(), username));
                            // Clear the input
                            input.setText("");
                        }

                        @Override
                        public void onCancelled(FirebaseError firebaseError) {
                        }
                    });
                }
            }
        });
        displayMessages();
    }

    private void displayMessages() {
        listOfMessages = (ListView)findViewById(R.id.list_of_messages);

        adapter = new FirebaseListAdapter<ChatMessage>(mChatRef, ChatMessage.class,
                R.layout.message_list_item, mCallingActivity) {
            @Override
            protected void populateView(View v, ChatMessage model) {
                // Get references to the views of message.xml
                TextView messageText = (TextView)v.findViewById(R.id.message_text);
                TextView messageUser = (TextView)v.findViewById(R.id.message_user);
                TextView messageTime = (TextView)v.findViewById(R.id.message_time);

                // Set their text
                messageText.setText(model.getMessageText());
                messageUser.setText(model.getUserName());

                 // Format the date before showing it
                messageTime.setText(DateFormat.format("HH:mm (dd-MM-yyyy)",
                        model.getMessageTime()));
                updateListView();
            }
        };
        listOfMessages.setAdapter(adapter);
        listOfMessages.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Log.e("TEST", "creatorID =" + creatorID + "   ___________ current ID " + FirebaseAuth.getInstance().getCurrentUser().getUid());
                    if (creatorID.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                        final int pos = position;
                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                        builder.setTitle("Game Winner")
                                .setMessage("Is this person the winner ?")
                                .setPositiveButton("OK",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                ChatMessage clickedMessage = (ChatMessage) listOfMessages.getItemAtPosition(pos);
                                                mMetaRef.child("winner").setValue(clickedMessage.getUserName());
                                                dismiss();
                                            }
                                        }
                                )
                                .setNegativeButton("Cancel",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int whichButton) {
                                                dialog.dismiss();
                                            }
                                        }
                                );
                        AlertDialog alertDialog = builder.create();
                        alertDialog.show();
                        return true;
                    }
                return false;
            }
        });
    }

    //This function will update ListView and will always be focused on the last message
    private void updateListView() {
        if (listOfMessages != null) {
            listOfMessages.setSelection(adapter.getCount() - 1);
        }
    }
}
