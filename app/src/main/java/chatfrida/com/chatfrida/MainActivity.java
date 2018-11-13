package chatfrida.com.chatfrida;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import ai.api.AIDataService;
import ai.api.AIListener;
import ai.api.AIServiceException;
import ai.api.android.AIConfiguration;
import ai.api.android.AIService;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Metadata;
import ai.api.model.Result;

public class MainActivity extends AppCompatActivity implements AIListener{

    RecyclerView recyclerView;
    EditText txtMsj;
    RelativeLayout addBtn;
    DatabaseReference ref;
    FirebaseRecyclerAdapter<ChatMensaje,ChatRecycler> adapter;
    Boolean flagFab = true;
    String nombre;

    private TextToSpeech mTextToSpeech;
    private AIService aiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recyclerView = findViewById(R.id.recyclerView);
        txtMsj = findViewById(R.id.txtMsj);
        addBtn = findViewById(R.id.addBtn);
        recyclerView.setHasFixedSize(true);

        recibirNombre();
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},1);

        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setStackFromEnd(true);

        recyclerView.setLayoutManager(linearLayoutManager);
        ref = FirebaseDatabase.getInstance().getReference("Usuarios/"+nombre);
        ref.keepSynced(false);

        final AIConfiguration config = new AIConfiguration("4de3402e39624c3aaceebabe2b3b4b36",
                                           AIConfiguration.SupportedLanguages.Spanish,
                                           AIConfiguration.RecognitionEngine.System);

        aiService = AIService.getService(this, config);
        aiService.setListener(this);

        final AIDataService aiDataService = new AIDataService(config);
        final AIRequest aiRequest = new AIRequest();

        mTextToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
            }
        });

        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                String message = txtMsj.getText().toString().trim();

                if (!message.equals("")) {

                    ChatMensaje chatMensaje = new ChatMensaje(message, "user");
                    ref.child("chat/").push().setValue(chatMensaje);

                    aiRequest.setQuery(message);
                    new AsyncTask<AIRequest,Void,AIResponse>(){

                        @Override
                        protected AIResponse doInBackground(AIRequest... aiRequests) {
                            final AIRequest request = aiRequests[0];
                            try {
                                final AIResponse response = aiDataService.request(aiRequest);
                                return response;
                            } catch (AIServiceException e) {
                            }
                            return null;
                        }
                        @Override
                        protected void onPostExecute(AIResponse response) {
                            if (response != null) {
                                Result result = response.getResult();
                                String reply = result.getFulfillment().getSpeech();
                                if (reply.isEmpty()){
                                    ChatMensaje chatMensaje = new ChatMensaje("no entiendo", "bot");
                                    ref.child("chat").push().setValue(chatMensaje);
                                }
                                else{
                                    ChatMensaje chatMensaje = new ChatMensaje(reply, "bot");
                                    ref.child("chat").push().setValue(chatMensaje);
                                }
                            }
                        }
                    }.execute(aiRequest);
                }
                else {
                    aiService.startListening();
                }
                txtMsj.setText("");
            }
        });

        txtMsj.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                ImageView fab_img = (ImageView)findViewById(R.id.btn_image);
                Bitmap img1 = BitmapFactory.decodeResource(getResources(),R.drawable.ic_send_white_24dp);
                Bitmap img2 = BitmapFactory.decodeResource(getResources(),R.drawable.ic_mic_white_24dp);

                if (s.toString().trim().length() != 0 && flagFab){
                    ImageViewAnimatedChange(MainActivity.this,fab_img,img1);
                    flagFab = false;
                }
                else if (s.toString().trim().length() == 0){
                    ImageViewAnimatedChange(MainActivity.this,fab_img,img2);
                    flagFab = true;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        adapter = new FirebaseRecyclerAdapter<ChatMensaje, ChatRecycler>(ChatMensaje.class,R.layout.msglist,ChatRecycler.class,ref.child("chat")) {
            @Override
            protected void populateViewHolder(ChatRecycler viewHolder, ChatMensaje model, int position) {
                if (model.getMsgUser().equals("user")) {
                    viewHolder.rightText.setText(model.getMsgText());
                    viewHolder.rightText.setVisibility(View.VISIBLE);
                    viewHolder.leftText.setVisibility(View.GONE);
                }
                else {
                    viewHolder.leftText.setText(model.getMsgText());
                    viewHolder.rightText.setVisibility(View.GONE);
                    viewHolder.leftText.setVisibility(View.VISIBLE);
                }
            }
        };

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);

                int msgCount = adapter.getItemCount();
                int lastVisiblePosition = linearLayoutManager.findLastCompletelyVisibleItemPosition();

                if (lastVisiblePosition == -1 ||
                        (positionStart >= (msgCount - 1) &&
                                lastVisiblePosition == (positionStart - 1))) {
                    recyclerView.scrollToPosition(positionStart);
                }
            }
        });
        recyclerView.setAdapter(adapter);

    }
    public void ImageViewAnimatedChange(Context c, final ImageView v, final Bitmap new_image) {
        final Animation anim_out = AnimationUtils.loadAnimation(c, R.anim.zoom_out);
        final Animation anim_in  = AnimationUtils.loadAnimation(c, R.anim.zoom_in);
        anim_out.setAnimationListener(new Animation.AnimationListener()
        {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation)
            {
                v.setImageBitmap(new_image);
                anim_in.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation animation) {}
                    @Override public void onAnimationRepeat(Animation animation) {}
                    @Override public void onAnimationEnd(Animation animation) {}
                });
                v.startAnimation(anim_in);
            }
        });
        v.startAnimation(anim_out);
    }


    public void recibirNombre(){
        Bundle extras = this.getIntent().getExtras();
        this.nombre = extras.getString("nombre");
    }

    @Override
    public void onResult(ai.api.model.AIResponse response) {
        final Result result = response.getResult();
        String message = result.getResolvedQuery();
        ChatMensaje chatMensaje0 = new ChatMensaje(message, "user");
        ref.child("chat").push().setValue(chatMensaje0);

        mTextToSpeech.speak(result.getFulfillment().getSpeech(), TextToSpeech.QUEUE_FLUSH, null, null);
        final String reply = result.getFulfillment().getSpeech();
        final Metadata metadata = result.getMetadata();

        if (metadata != null) {
            if(metadata.getIntentName().equals("Ubicacion")){
                ref.child("Ubicacion/Direccion").addValueEventListener(new ValueEventListener() {
                    String ubicacion;

                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                         ubicacion = dataSnapshot.getValue().toString();
                         ChatMensaje chatMensaje = new ChatMensaje(reply +" : "+ ubicacion, "bot");
                         ref.child("chat").push().setValue(chatMensaje);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                    }

                });
            } else{
                Toast.makeText(this, metadata.getIntentName(), Toast.LENGTH_SHORT).show();
                ChatMensaje chatMensaje = new ChatMensaje(reply, "bot");
                ref.child("chat").push().setValue(chatMensaje);
            }
        }
    }


    @Override
    public void onError(ai.api.model.AIError error) {

    }

    @Override
    public void onAudioLevel(float level) {

    }

    @Override
    public void onListeningStarted() {
        Toast.makeText(this, "Escuchando", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onListeningCanceled() {

    }

    @Override
    public void onListeningFinished() {
        Toast.makeText(this, "mensaje finalizado", Toast.LENGTH_SHORT).show();
    }
}