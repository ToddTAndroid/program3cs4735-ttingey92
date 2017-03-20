package edu.uwyo.toddt.tic_tac_toe;
// Todd Tingey

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/* Created by Todd Tingey
   Edited 3/15/2017 for Bluetooth play
 */

public class MainActivity extends AppCompatActivity {

    // Variables for the activity and tic tac toe board
    Context context = this;
    ViewSwitcher vSwitcher;
    Button start;
    Button playAgain;
    Button quit;
    LinearLayout winScreen;
    TextView winText;
    TextView loseText;
    Bitmap gameBoard;
    Canvas gameBoardC;
    ImageView gameBoardField;
    final int boardsize = 900;
    int boardPos = 0;
    int[] boardKey = new int[] {0,0,0,0,0,0,0,0,0}; // used to determine if a space has X, O, or nothing
                                                    // 0 = unoccupied, 1 = occupied X, 2 = occupied O
    boolean isPlayerTurn;

    // Drawing resources
    Paint myColor;
    Bitmap xImage;
    Bitmap oImage;

    // --- Bluetooth resources ---
    public static final UUID MY_UUID = UUID.fromString("e2194bbc-a094-490e-aac1-afb22f3cb80c");
    public static final String NAME = "BluetoothTicTacToe";

    BluetoothAdapter mBluetoothAdapter = null;
    BluetoothDevice device;
    Boolean isServer;       // used to determine if player is server or client
    Boolean isX;        // used to determine if player is x's or 0's
    Boolean isGameStart = true;     // used to identify the first turn of a game
    Boolean sendReady = false;      // used to indicate when a player can send a placement command
    Boolean newGame = false;      // indicates player would like to play again
    Boolean endGame = false;        // indicates player would like to end game
    String command;     // string stores user command to send


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vSwitcher = (ViewSwitcher) findViewById(R.id.vs_activity_main);
        winText = (TextView) findViewById(R.id.txt_winner);
        loseText = (TextView) findViewById(R.id.txt_loser);
        winScreen = (LinearLayout) findViewById(R.id.view_win);
        final AlertDialog alert;

        // Preliminary questions
        String[] items = {"Server", "Client"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose status:");
        builder.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int item){
                dialog.dismiss();
                if(item == 0){
                    isServer = true;
                } else {
                    isServer = false;
                }
            }
        });
        alert = builder.create();
        alert.show();

        // set up buttons
        start = (Button) findViewById(R.id.btn_start);
        start.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(isServer != null){
                    if(isServer){
                        // player chose server
                        startServer();
                    } else {
                        // player chose client (startClient() called at end of querypaired())
                        querypaired();
                    }
                    gameBoardField.setVisibility(View.VISIBLE);
                    start.setVisibility(View.GONE);
                } else {
                    alert.show();
                }
            }
        });

        playAgain = (Button) findViewById(R.id.btn_play_again);
        playAgain.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                resetGame();
                playAgain.setVisibility(View.INVISIBLE);
                quit.setVisibility(View.INVISIBLE);
                if(isServer){
                    // ask again for x's or o's
                    String[] items = {"X's", "O's"};
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle("Choose Player:");
                    builder.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int item){
                            dialog.dismiss();
                            if(item == 0){
                                isX = true;
                                isPlayerTurn = true;
                            } else {
                                isX = false;
                                isPlayerTurn = false;
                            }
                            newGame = true;
                        }
                    });
                    AlertDialog alert = builder.create();
                    alert.show();

                    // set turn
                    isPlayerTurn = true;
                } else {
                    newGame = true;
                }
                Toast.makeText(context, "Selected play again", Toast.LENGTH_SHORT).show();
            }
        });

        quit = (Button) findViewById(R.id.btn_quit);
        quit.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                endGame = true;
                winText.setText("Game over");
                loseText.setText("");
                playAgain.setVisibility(View.GONE);
                quit.setVisibility(View.GONE);
            }
        });

        // Set up the game board
        gameBoardField = (ImageView) findViewById(R.id.boardfield);
        gameBoard = Bitmap.createBitmap(boardsize, boardsize, Bitmap.Config.ARGB_8888);
        gameBoardC = new Canvas(gameBoard);
        gameBoardC.drawColor(Color.WHITE);
        gameBoardField.setImageBitmap(gameBoard);
        gameBoardField.setOnTouchListener(new myTouchListener());
        gameBoardField.setVisibility(View.GONE);

        // Set up drawing utilities
        myColor = new Paint();
        myColor.setStyle(Paint.Style.FILL);
        myColor.setStrokeWidth(10);
        myColor.setTextSize(myColor.getTextSize() * 4);

        // Draw lines on board
        drawLines();

        // Load the pictures
        xImage = BitmapFactory.decodeResource(getResources(), R.drawable.image_x);
        xImage = Bitmap.createScaledBitmap(xImage, 250, 250, false);
        oImage = BitmapFactory.decodeResource(getResources(), R.drawable.image_o);
        oImage = Bitmap.createScaledBitmap(oImage, 250, 257, false);

        // Set up Bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null){
            // No bluetooth
            Log.v("main", "No bluetooth available.");
        }

    }

    // ----------- Handlers for Bluetooth threads -----------
    // Makes toasts for debugging purposes
    private android.os.Handler handler = new android.os.Handler(new android.os.Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            String tmp = msg.getData().getString("msg");
            Toast.makeText(context, tmp, Toast.LENGTH_SHORT).show();
            return true;
        }
    });

    // Places an oponent's symbol
    private android.os.Handler handler2 = new android.os.Handler(new android.os.Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            String tmp = msg.getData().getString("msg");
            int pos = Integer.parseInt(tmp);
            placeOpponent(pos);
            return true;
        }
    });

    // loads win screens or re-loads gameboard for additional game
    private android.os.Handler handler3 = new android.os.Handler(new android.os.Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            String str = msg.getData().getString("msg");
            if (str != null) {
                switch (str){
                    case "xWinner":
                        xWin();
                        break;
                    case "oWinner":
                        oWin();
                        break;
                    case "tie":
                        playerTie();
                        break;
                    case "playagain":
                        // load board
                        vSwitcher.showPrevious();

                        break;
                    case "denied":
                        winText.setText("Game over");
                        loseText.setText("");
                        playAgain.setVisibility(View.GONE);
                        quit.setVisibility(View.GONE);
                        Toast.makeText(context, "Opponent ended game", Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        Log.v("handler3", "invalid msg");
                }
            }
            return true;
        }
    });

    // Handler message passers
    public void mkmsg(String str) {
        Message msg = new Message();
        Bundle b = new Bundle();
        b.putString("msg", str);
        msg.setData(b);
        handler.sendMessage(msg);
    }

    public void placemsg(String str){
        Message msg = new Message();
        Bundle b = new Bundle();
        b.putString("msg", str);
        msg.setData(b);
        handler2.sendMessage(msg);
    }

    public void winmsg(String str){
        Message msg = new Message();
        Bundle b = new Bundle();
        b.putString("msg", str);
        msg.setData(b);
        handler3.sendMessage(msg);
    }

    class myTouchListener implements View.OnTouchListener{
        @Override
        public boolean onTouch(View v, MotionEvent event){

            if(event.getAction() == MotionEvent.ACTION_UP){
                placeSymbol((int) event.getX(), (int) event.getY());
                return true;
            }

            return false;
        }
    }

    // places player symbol
    void placeSymbol(float x, float y){
        // Determine board position
        if(isPlayerTurn){
            if(y <= 300){
                if(x <= 300){
                    boardPos = 1;
                }
                else if(x <= 600){
                    boardPos = 2;
                }
                else {
                    boardPos = 3;
                }
            }
            else if(y <= 600){
                if(x <= 300){
                    boardPos = 4;
                }
                else if(x <= 600){
                    boardPos = 5;
                }
                else {
                    boardPos = 6;
                }
            }
            else {
                if(x <= 300){
                    boardPos = 7;
                }
                else if(x <= 600){
                    boardPos = 8;
                }
                else {
                    boardPos = 9;
                }
            }
            if(isX){
                switch (boardPos){
                    case 1:
                        if(boardKey[0] == 0) {
                            gameBoardC.drawBitmap(xImage, 25, 25, myColor);
                            boardKey[0] = 1;
                            command = "1";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 2:
                        if(boardKey[1] == 0){
                            gameBoardC.drawBitmap(xImage, 325, 25, myColor);
                            boardKey[1] = 1;
                            command = "2";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 3:
                        if(boardKey[2] == 0){
                            gameBoardC.drawBitmap(xImage, 625, 25, myColor);
                            boardKey[2] = 1;
                            command = "3";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 4:
                        if(boardKey[3] == 0){
                            gameBoardC.drawBitmap(xImage, 25, 325, myColor);
                            boardKey[3] = 1;
                            command = "4";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 5:
                        if(boardKey[4] == 0){
                            gameBoardC.drawBitmap(xImage, 325, 325, myColor);
                            boardKey[4] = 1;
                            command = "5";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 6:
                        if(boardKey[5] == 0){
                            gameBoardC.drawBitmap(xImage, 625, 325, myColor);
                            boardKey[5] = 1;
                            command = "6";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 7:
                        if(boardKey[6] == 0){
                            gameBoardC.drawBitmap(xImage, 25, 625, myColor);
                            boardKey[6] = 1;
                            command = "7";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 8:
                        if(boardKey[7] == 0){
                            gameBoardC.drawBitmap(xImage, 325, 625, myColor);
                            boardKey[7] = 1;
                            command = "8";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 9:
                        if(boardKey[8] == 0){
                            gameBoardC.drawBitmap(xImage, 625, 625, myColor);
                            boardKey[8] = 1;
                            command = "9";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    default:
                        Log.v("msg", "I broke: " + boardPos);
                }

            }
            else {
                switch (boardPos) {
                    case 1:
                        if(boardKey[0] == 0) {
                            gameBoardC.drawBitmap(oImage, 25, 25, myColor);
                            boardKey[0] = 2;
                            command = "1";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 2:
                        if(boardKey[1] == 0){
                            gameBoardC.drawBitmap(oImage, 325, 25, myColor);
                            boardKey[1] = 2;
                            command = "2";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 3:
                        if(boardKey[2] == 0){
                            gameBoardC.drawBitmap(oImage, 625, 25, myColor);
                            boardKey[2] = 2;
                            command = "3";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 4:
                        if(boardKey[3] == 0){
                            gameBoardC.drawBitmap(oImage, 25, 325, myColor);
                            boardKey[3] = 2;
                            command = "4";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 5:
                        if(boardKey[4] == 0){
                            gameBoardC.drawBitmap(oImage, 325, 325, myColor);
                            boardKey[4] = 2;
                            command = "5";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 6:
                        if(boardKey[5] == 0){
                            gameBoardC.drawBitmap(oImage, 625, 325, myColor);
                            boardKey[5] = 2;
                            command = "6";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 7:
                        if(boardKey[6] == 0){
                            gameBoardC.drawBitmap(oImage, 25, 625, myColor);
                            boardKey[6] = 2;
                            command = "7";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 8:
                        if(boardKey[7] == 0){
                            gameBoardC.drawBitmap(oImage, 325, 625, myColor);
                            boardKey[7] = 2;
                            command = "8";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    case 9:
                        if(boardKey[8] == 0){
                            gameBoardC.drawBitmap(oImage, 625, 625, myColor);
                            boardKey[8] = 2;
                            command = "9";
                            sendReady = true;
                            isPlayerTurn = false;
                        }
                        break;
                    default:
                        Log.v("placeSymbol", "I broke: " + boardPos);
                }

            }

            gameBoardField.setImageBitmap(gameBoard);
            gameBoardField.invalidate();
        }
    }

    // Function for placing an opponent's symbol
    public void placeOpponent(int pos){
        if(!isX){
            switch (pos){
                case 1:
                    if(boardKey[0] == 0) {
                        gameBoardC.drawBitmap(xImage, 25, 25, myColor);
                        boardKey[0] = 1;
                    }
                    break;
                case 2:
                    if(boardKey[1] == 0){
                        gameBoardC.drawBitmap(xImage, 325, 25, myColor);
                        boardKey[1] = 1;
                    }
                    break;
                case 3:
                    if(boardKey[2] == 0){
                        gameBoardC.drawBitmap(xImage, 625, 25, myColor);
                        boardKey[2] = 1;
                    }
                    break;
                case 4:
                    if(boardKey[3] == 0){
                        gameBoardC.drawBitmap(xImage, 25, 325, myColor);
                        boardKey[3] = 1;
                    }
                    break;
                case 5:
                    if(boardKey[4] == 0){
                        gameBoardC.drawBitmap(xImage, 325, 325, myColor);
                        boardKey[4] = 1;
                    }
                    break;
                case 6:
                    if(boardKey[5] == 0){
                        gameBoardC.drawBitmap(xImage, 625, 325, myColor);
                        boardKey[5] = 1;
                    }
                    break;
                case 7:
                    if(boardKey[6] == 0){
                        gameBoardC.drawBitmap(xImage, 25, 625, myColor);
                        boardKey[6] = 1;
                    }
                    break;
                case 8:
                    if(boardKey[7] == 0){
                        gameBoardC.drawBitmap(xImage, 325, 625, myColor);
                        boardKey[7] = 1;
                    }
                    break;
                case 9:
                    if(boardKey[8] == 0){
                        gameBoardC.drawBitmap(xImage, 625, 625, myColor);
                        boardKey[8] = 1;
                    }
                    break;
                default:
                    Log.v("msg", "I broke: " + boardPos);
            }

        }
        else {
            switch (pos) {
                case 1:
                    if(boardKey[0] == 0) {
                        gameBoardC.drawBitmap(oImage, 25, 25, myColor);
                        boardKey[0] = 2;
                    }
                    break;
                case 2:
                    if(boardKey[1] == 0){
                        gameBoardC.drawBitmap(oImage, 325, 25, myColor);
                        boardKey[1] = 2;
                    }
                    break;
                case 3:
                    if(boardKey[2] == 0){
                        gameBoardC.drawBitmap(oImage, 625, 25, myColor);
                        boardKey[2] = 2;
                    }
                    break;
                case 4:
                    if(boardKey[3] == 0){
                        gameBoardC.drawBitmap(oImage, 25, 325, myColor);
                        boardKey[3] = 2;
                    }
                    break;
                case 5:
                    if(boardKey[4] == 0){
                        gameBoardC.drawBitmap(oImage, 325, 325, myColor);
                        boardKey[4] = 2;
                    }
                    break;
                case 6:
                    if(boardKey[5] == 0){
                        gameBoardC.drawBitmap(oImage, 625, 325, myColor);
                        boardKey[5] = 2;
                    }
                    break;
                case 7:
                    if(boardKey[6] == 0){
                        gameBoardC.drawBitmap(oImage, 25, 625, myColor);
                        boardKey[6] = 2;
                    }
                    break;
                case 8:
                    if(boardKey[7] == 0){
                        gameBoardC.drawBitmap(oImage, 325, 625, myColor);
                        boardKey[7] = 2;
                    }
                    break;
                case 9:
                    if(boardKey[8] == 0){
                        gameBoardC.drawBitmap(oImage, 625, 625, myColor);
                        boardKey[8] = 2;
                    }
                    break;
                default:
                    Log.v("msg", "I broke: " + boardPos);
            }

        }

        gameBoardField.setImageBitmap(gameBoard);
        gameBoardField.invalidate();
    }

    // X win condition
    // int values indicate win status: nowinner(0), winner(1), tie(2)
    public int checkXwin(){

        int count = 0;
        for(int key : boardKey){
            if(key != 0){
                count++;
            }
        }

        if(boardKey[0] == 1 && boardKey[1]==1 && boardKey[2]==1){
            return 1;
        }
        else if(boardKey[3] == 1 && boardKey[4]==1 && boardKey[5]==1){
            return 1;
        }
        else if(boardKey[6] == 1 && boardKey[7]==1 && boardKey[8]==1){
            return 1;
        }
        else if(boardKey[0] == 1 && boardKey[3]==1 && boardKey[6]==1){
            return 1;
        }
        else if(boardKey[1] == 1 && boardKey[4]==1 && boardKey[7]==1){
            return 1;
        }
        else if(boardKey[2] == 1 && boardKey[5]==1 && boardKey[8]==1){
            return 1;
        }
        else if(boardKey[0] == 1 && boardKey[4]==1 && boardKey[8]==1){
            return 1;
        }
        else if(boardKey[2] == 1 && boardKey[4]==1 && boardKey[6]==1){
            return 1;
        }
        else if(count == 9){
            return 2;
        }
        else {
            return 0;
        }
    }

    // O win condition
    // nowinner(0), winner(1), tie(2)
    public int checkOwin(){

        int count = 0;
        for(int key : boardKey){
            if(key != 0){
                count++;
            }
        }

        if(boardKey[0] == 2 && boardKey[1]==2 && boardKey[2]==2){
            return 1;
        }
        else if(boardKey[3] == 2 && boardKey[4]==2 && boardKey[5]==2){
            return 1;
        }
        else if(boardKey[6] == 2 && boardKey[7]==2 && boardKey[8]==2){
            return 1;
        }
        else if(boardKey[0] == 2 && boardKey[3]==2 && boardKey[6]==2){
            return 1;
        }
        else if(boardKey[1] == 2 && boardKey[4]==2 && boardKey[7]==2){
            return 1;
        }
        else if(boardKey[2] == 2 && boardKey[5]==2 && boardKey[8]==2){
            return 1;
        }
        else if(boardKey[0] == 2 && boardKey[4]==2 && boardKey[8]==2){
            return 1;
        }
        else if(boardKey[2] == 2 && boardKey[4]==2 && boardKey[6]==2){
            return 1;
        }
        else if(count == 9){
            return 2;
        }
        else {
            return 0;
        }
    }

    // loads x win screen
    void xWin(){
        String text = "Player X wins!";
        winText.setText(text);
        winText.setTextColor(Color.BLACK);
        text = "O loses";
        loseText.setText(text);
        loseText.setTextColor(Color.BLACK);
        winScreen.setBackgroundResource(R.drawable.fragonard_swing);
        playAgain.setVisibility(View.VISIBLE);
        quit.setVisibility(View.VISIBLE);
        vSwitcher.showNext();
    }

    // loads o win screen
    void oWin(){
        String text = "Player O wins!";
        winText.setText(text);
        winText.setTextColor(Color.BLACK);
        text = "X loses";
        loseText.setText(text);
        loseText.setTextColor(Color.BLACK);
        winScreen.setBackgroundResource(R.drawable.wanderer_small);
        playAgain.setVisibility(View.VISIBLE);
        quit.setVisibility(View.VISIBLE);
        vSwitcher.showNext();
    }

    // loads tie screen
    void playerTie(){
        String text = "It's a tie!";
        winText.setText(text);
        winText.setTextColor(Color.WHITE);
        loseText.setText("");
        winScreen.setBackgroundResource(R.drawable.the_night);
        playAgain.setVisibility(View.VISIBLE);
        quit.setVisibility(View.VISIBLE);
        vSwitcher.showNext();
    }

    // Draw hash lines on board
    void drawLines(){
        gameBoardC.drawLine(300, 0, 300, 900, myColor);
        gameBoardC.drawLine(0, 300, 900, 300, myColor);
        gameBoardC.drawLine(600, 0, 600, 900, myColor);
        gameBoardC.drawLine(0, 600, 900, 600, myColor);
    }

    void resetGame(){
        for(int i=0; i < 9; i++){
            boardKey[i] = 0;
        }
        gameBoardC.drawColor(Color.WHITE);
        drawLines();
        gameBoardField.setImageBitmap(gameBoard);
        gameBoardField.invalidate();
    }

    // Ask for the device with which to communicate. Then begin client thread.
    /* referenced from blueToothDemo */
    public void querypaired(){
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0) {
            final BluetoothDevice blueDev[] = new BluetoothDevice[pairedDevices.size()];
            String[] items = new String[blueDev.length];
            int i = 0;
            for (BluetoothDevice devicel : pairedDevices){
                blueDev[i] = devicel;
                items[i] = blueDev[i].getName() + ": " + blueDev[i].getAddress();
                i++;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Choose device:");
            builder.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog, int item){
                    dialog.dismiss();
                    if(item >= 0 && item < blueDev.length){
                        device = blueDev[item];
                        startClient();      // start client once device selected
                    }
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    // ---------- Client thread ----------
    /* setup referenced from blueToothDemo */
    public void startClient(){
        if (device != null){
            new Thread(new ConnectThread(device)).start();
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket socket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            // Get Bluetooth socket
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.v("client", "connection failed");
            }
            socket = tmp;
        }

        public void run(){
            mBluetoothAdapter.cancelDiscovery();


            // Make connection
            try {
                socket.connect();
            } catch (IOException e) {
                Log.v("client", "Connect failed");
                try {
                    socket.close();
                    socket = null;
                } catch (IOException e2) {
                    Log.v("client", "close() socket failed");
                    socket = null;
                }
            }

            if(socket != null) {
                Log.v("client", "Connection made");
                mkmsg("Connection success");
                // begin message sending
                try {
                    String str;
                    Boolean done = false;
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter( new BufferedWriter( new OutputStreamWriter(socket.getOutputStream())),true);

                    str = in.readLine();

                    do{

                        switch (str){
                            case "Player X":
                                out.println("agree");
                                out.flush();
                                isX = false;
                                isPlayerTurn = false;
                                str = in.readLine();   // wait for opponent to make first move
                                break;
                            case "Player O":
                                out.println("agree");
                                out.flush();
                                isX = true;
                                isPlayerTurn = true;
                                break;
                            case "agree":
                                str = in.readLine();
                                break;
                            case "1":
                                if(boardKey[0] == 0) {
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "2":
                                if(boardKey[1] == 0) {
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "3":
                                if(boardKey[2] == 0) {
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "4":
                                if(boardKey[3] == 0) {
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "5":
                                if(boardKey[4] == 0) {
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "6":
                                if(boardKey[5] == 0) {
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "7":
                                if(boardKey[6] == 0) {
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "8":
                                if(boardKey[7] == 0) {
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "9":
                                if(boardKey[8] == 0) {
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "nowinner":
                                if ((checkXwin() == 0) && (checkOwin() == 0)){
                                    out.println("agree");
                                    out.flush();
                                    isPlayerTurn = true;
                                }
                                str="";
                                break;
                            case "winner":
                                if((checkXwin() == 1) || (checkOwin() == 1)){
                                    out.println("agree");
                                    out.flush();
                                    if(checkXwin() == 1){
                                        winmsg("xWinner");
                                    }
                                    else {
                                        winmsg("oWinner");
                                    }
                                }
                                str="";
                                break;
                            case "tie":
                                if ((checkXwin() == 2) && (checkOwin() == 2)){
                                    out.println("agree");
                                    out.flush();
                                    winmsg("tie");
                                }
                                str="";
                                break;
                        }
                        if(sendReady){
                            out.println(command);
                            out.flush();
                            sendReady = false;
                            str = in.readLine();
                            if( str.equals("agree")){
                                if((checkXwin() == 0) && (checkOwin() == 0)){
                                    out.println("nowinner");
                                    out.flush();
                                    str = in.readLine();
                                }
                                else if ((checkXwin() == 1) || (checkOwin() == 1)){
                                    out.println("winner");
                                    out.flush();
                                    str = in.readLine();
                                    if(str.equals("agree")){
                                        if(checkXwin() == 1){
                                            winmsg("xWinner");
                                        }
                                        else {
                                            winmsg("oWinner");
                                        }
                                    }
                                    str = "";
                                }
                                else if((checkXwin() == 2) && (checkOwin() == 2)) {
                                    out.println("tie");
                                    out.flush();
                                    str = in.readLine();
                                    if(str.equals("agree")){
                                        winmsg("tie");
                                    }
                                    str = "";
                                }
                            }
                        }
                        if(newGame){
                            str = in.readLine();
                            if(str.equals("playagain")){
                                out.println("agree");
                                out.flush();
                                winmsg("playagain");
                                str = in.readLine(); // wait for player to re-choose symbol
                            } else {
                                winmsg("denied");
                                done = true;
                            }
                            newGame = false;
                        }
                        if(endGame){
                            in.readLine();
                            out.println("disagree");
                            out.flush();
                            done = true;
                        }

                    } while(!done);

                } catch (IOException e) {
                    Log.v("client", "error message sending");
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        Log.v("client", "error closing socket");
                    }
                }
            } else {
                Log.v("client", "Connected, but null socket");
            }
        }

    }

    // ---------- Server thread ----------
    /* setup referenced from blueToothDemo */
    public void startServer(){
        // first determine if player wants x's or o's
        String[] items = {"X's", "O's"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Player:");
        builder.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface dialog, int item){
                dialog.dismiss();
                if(item == 0){
                    isX = true;
                    isPlayerTurn = true;
                } else {
                    isX = false;
                    isPlayerTurn = false;
                }
                // begin thread process on selection
                new Thread(new AcceptThread()).start();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private class AcceptThread extends Thread {
        // local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;
            // create listening server socket
            try{
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e){
                Log.v("server", "failed to start");
            }
            mmServerSocket = tmp;
        }

        public void run(){
            BluetoothSocket socket = null;
            try {
                socket = mmServerSocket.accept();
            } catch (IOException e) {
                Log.v("server", "failed to accept");
            }

            if(socket != null) {

                try {
                    String str = "";
                    Boolean done = false;
                    Boolean waitForCommand = false;
                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    do{
                        if(isGameStart){
                            if(isX){
                                out.println("Player X");
                                out.flush();
                                str = in.readLine();
                            }
                            else{
                                out.println("Player O");
                                out.flush();
                                str = in.readLine();
                            }

                            isGameStart = false;
                            if(str.equals("agree")){
                                if(!isX){
                                    str = in.readLine(); // wait for opponent first move
                                } else {
                                    str = "";   // loop until command ready
                                }

                            }
                        }

                        switch (str){
                            case "agree":
                                str = in.readLine();
                                break;
                            case "1":
                                if(boardKey[0] == 0){
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "2":
                                if(boardKey[1] == 0){
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "3":
                                if(boardKey[2] == 0){
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "4":
                                if(boardKey[3] == 0){
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "5":
                                if(boardKey[4] == 0){
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "6":
                                if(boardKey[5] == 0){
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "7":
                                if(boardKey[6] == 0){
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "8":
                                if(boardKey[7] == 0){
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "9":
                                if(boardKey[8] == 0){
                                    placemsg(str);
                                    out.println("agree");
                                    out.flush();
                                    str = in.readLine();
                                }
                                break;
                            case "nowinner":
                                if ((checkXwin() == 0) && (checkOwin() == 0)){
                                    out.println("agree");
                                    out.flush();
                                    isPlayerTurn = true;
                                }
                                str = "";
                                break;
                            case "winner":
                                if((checkXwin() == 1) || (checkOwin() == 1)){
                                    out.println("agree");
                                    out.flush();
                                    if(checkXwin() == 1){
                                        winmsg("xWinner");
                                    }
                                    else {
                                        winmsg("oWinner");
                                    }
                                }
                                str = "";
                                break;
                            case "tie":
                                if ((checkXwin() == 2) && (checkOwin() == 2)){
                                    out.println("agree");
                                    out.flush();
                                    winmsg("tie");
                                }
                                str = "";
                                break;
                        }
                        if(sendReady){
                            out.println(command);
                            out.flush();
                            sendReady = false;
                            str = in.readLine();
                            if( str.equals("agree")) {
                                if ((checkXwin() == 0) && (checkOwin() == 0)) {
                                    out.println("nowinner");
                                    out.flush();
                                    str = in.readLine();
                                } else if ((checkXwin() == 1) || (checkOwin() == 1)) {
                                    out.println("winner");
                                    out.flush();
                                    str = in.readLine();
                                    if(str.equals("agree")){
                                        if(checkXwin() == 1){
                                            winmsg("xWinner");
                                        }
                                        else {
                                            winmsg("oWinner");
                                        }
                                    }
                                    str = "";
                                } else if ((checkXwin() == 2) && (checkOwin() == 2)) {
                                    out.println("tie");
                                    out.flush();
                                    str = in.readLine();
                                    if(str.equals("agree")){
                                        winmsg("tie");
                                    }
                                    str = "";
                                }
                            }
                        }
                        if(newGame){
                            out.println("playagain");
                            out.flush();
                            str = in.readLine();
                            if(str.equals("agree")){
                                isGameStart = true;
                                winmsg("playagain");
                            } else {
                                winmsg("denied");
                                done = true;
                            }
                            newGame = false;
                        }
                        if(endGame){
                            out.println("exit");
                            out.flush();
                            done = true;
                        }

                    } while (!done);

                } catch (IOException e){
                    Log.v("server", "Error sending/receiving msg");
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e){
                        Log.v("server", "unable to close socket");
                    }
                }
            } else {
                Log.v("server", "connected, but null socket");
            }
        }
    }

}








