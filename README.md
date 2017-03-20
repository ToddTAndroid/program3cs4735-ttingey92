# Program # 3
Name:  Todd Tingey
Cosc 4010

Description:  

	Before use, ensure the desired devices are paired. 

	After opening the app it will imidiately prompt if you want to be the server or client.
	Select an option then press start (if you did not select an option, by clicking outside the dialog or
	pressing the back button, the start button will prompt you again to select server or client).
	For the server, after start is pressed it will prompt you to choose x's or o's. Then, it begins the server
	thread and the game begins.
	For the client, after start is pressed it will prompt you to choose the paired bluetooth device you wish to
	communicate with. Then, it begins the client thread and upon successful connection the game begins. 

	Player x always moves first. Place a symbol simply by touching the desired board location. Upon successful
	placement, you will not be able to place a symbol until it is your turn again. 

	Once a player has won or there is a tie, the win/tie screen will appear with the results. At the bottom of
	these screens are the "play again" and "quit" buttons. If the server chooses to play again he/she is prompted
	to choose x's or o's again, and then they wait for the client to agree. If the client chooses play again
	it will simply wait for the server to decide. If they both play again the board resets and player x goes 
	first. If either or both decide to quit, the game ends. 

	Phones: Google Pixel, 5.0" (Nougat 7.1) & Samsung Galaxy SIII, 4.8" (KitKat 4.4.2)

Anything that doesn't work:

	Between the phones I've tested everything should be working as expected. However, occasionally the SIII
	won't connect to the server as a client, but it will always connect if it is the server to a client device.
	This was an issue just with the SIII I used. I'm not sure if this is because of an error on the phone or 
	because of an issue with it's android version. I just mention it because it could also be a coding error (but
	hopefully not).
	Thanks!