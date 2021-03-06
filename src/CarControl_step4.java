//Mandatory assignment
//Prototype implementation of Car Control
//Course 02158 Concurrent Programming, DTU, Fall 2015

//Hans Henrik Løvengreen    Oct 6,  2015


import java.awt.Color;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;

class Gate {

	Semaphore g = new Semaphore(0);
	Semaphore e = new Semaphore(1);
	boolean isopen = false;

	public void pass() throws InterruptedException {
		g.P(); 
		g.V();
	}

	public void open() {
		try { e.P(); } catch (InterruptedException e) {}
		if (!isopen) { g.V();  isopen = true; }
		e.V();
	}

	public void close() {
		try { e.P(); } catch (InterruptedException e) {}
		if (isopen) { 
			try { g.P(); } catch (InterruptedException e) {}
			isopen = false;
		}
		e.V();
	}
	public boolean getIsOpen(){
		return isopen;
	}
}


class Alley{

	Semaphore alleySem = new Semaphore(1);	// Semaphore indicating whether a group of cars can enter the alley.
	Semaphore teamUpSem = new Semaphore(1);	// Semaphore to grant both part of teamUp access to alley	
	boolean teamUp, teamDown = false;		
	int noCars = 0;							// Counter to keep track of cars in alley

	public synchronized void enter(int no){
		//Denying access for cars 1-4 (if cars from 5-8 are in the alley)
		if(4 < no){ 
			if(teamDown){
				noCars ++;

			}else{
				try{ alleySem.P();} catch (InterruptedException e) {}
				teamDown = true;	
				noCars++;
			}
		} 
		//Denying access for cars 5-8 (if cars from 1-4 are in the alley)
		if(no < 5){ 
			if(teamUp){
				noCars ++;
			}else{
				try{ teamUpSem.P();
				if(!teamUp){
					try {alleySem.P();} catch (InterruptedException e) {}
				} 
				} catch (InterruptedException e) {}
				teamUp = true;
				noCars ++;
				teamUpSem.V();
			}	
		}

	}

	public void leave(int no){
		// Check which cars is leaving the alley
		if(4 < no){
			noCars --;
			if(noCars == 0){
				teamDown = false;
				alleySem.V();
			}

		}else if(no < 5){ 
			noCars --;
			if(noCars == 0){
				teamUp = false;
				alleySem.V();
			}
		}
	}	
}

class Barrier {

	// Two-phase barrier
	// Method from The Little Book Of Semaphores, B. Downey, Allan

	int cars = 9;									// No. of cars in all (no distinction between active/inactive cars)
	int count = 0;
	boolean barrierOn, barrierLock, shutdownOn = false;			// Boolean to check if barrier is on

	public synchronized void sync(Pos pos) {    // Wait for others to arrive (if barrier active)

		// Release cars when all have reached the barrier
		while(barrierLock){
			try{ wait();} catch (InterruptedException e) {}
		}
		count ++;
		if(count == cars){
			barrierLock = true;
			notifyAll();
		}

		// Make cars wait at barrier
		while(!barrierLock){
			try{ wait();} catch (InterruptedException e) {}
		}
		count --;	
		if(count == 0){
			barrierLock = false;
			notifyAll();
		}
	}

	public void on() { // Activate barrier
		barrierOn = true;
	}    

	public void off() { // Deactivate barrier
		barrierOn = false;
	}   
	
	public void shutdown() {
		
		if(shutdownOn){
			
		}else{	
			barrierOn = false;
			shutdownOn = false;
		}
	}
	
	public boolean getShutdownOn(){
		return shutdownOn;
	}
	public void setShutdownOn(boolean bool){
		shutdownOn = bool;
	}

	public boolean getBarrierOn(){
		return barrierOn;
	}
}


class Car extends Thread {

	int basespeed = 100;             // Rather: degree of slowness
	int variation =  50;             // Percentage of base speed

	CarDisplayI cd;                  // GUI part

	int no;                          // Car number
	Pos startpos;                    // Starting position (provided by GUI)
	Pos barpos;                      // Barrier position (provided by GUI)
	Color col;                       // Car  color
	Gate mygate;                     // Gate at starting position

	int speed;                       // Current car speed
	Pos curpos;                      // Current position 
	Pos newpos;                      // New position to go to
	Semaphore[][] semaphores;        // Arrays of semaphores to keep track of 'legal' positions
	Alley alley;					 // the alley in which the cars pass through
	Barrier barrier;

	public Car(int no, CarDisplayI cd, Gate g, Semaphore[][] semaphores, Alley alley, Barrier barrier) {

		this.no = no;
		this.cd = cd;
		this.semaphores = semaphores;
		this.alley = alley;
		this.barrier = barrier;
		mygate = g;
		startpos = cd.getStartPos(no);
		barpos = cd.getBarrierPos(no);  // For later use

		col = chooseColor();

		// do not change the special settings for car no. 0
		if (no==0) {
			basespeed = 0;  
			variation = 0; 
			setPriority(Thread.MAX_PRIORITY); 
		}
	}

	public synchronized void setSpeed(int speed) { 
		if (no != 0 && speed >= 0) {
			basespeed = speed;
		}
		else
			cd.println("Illegal speed settings");
	}

	public synchronized void setVariation(int var) { 
		if (no != 0 && 0 <= var && var <= 100) {
			variation = var;
		}
		else
			cd.println("Illegal variation settings");
	}

	synchronized int chooseSpeed() { 
		double factor = (1.0D+(Math.random()-0.5D)*2*variation/100);
		return (int)Math.round(factor*basespeed);
	}

	private int speed() {
		// Slow down if requested
		final int slowfactor = 3;  
		return speed * (cd.isSlow(curpos)? slowfactor : 1);
	}

	Color chooseColor() { 
		return Color.blue; // You can get any color, as longs as it's blue 
	}

	Pos nextPos(Pos pos) {
		// Get my track from display
		return cd.nextPos(no,pos);

	}

	boolean atGate(Pos pos) {
		return pos.equals(startpos);
	}

	boolean inAlley(Pos pos){
		Pos posTop = new Pos(0, 0);			// Entry for cars 5-8
		Pos posBot1 = new Pos(8, 1);		// Entry for cars 1-4
		Pos posBot2 = new Pos(9, 3);		// Entry for cars 1-4
		if(pos.equals(posTop)||pos.equals(posBot1)||pos.equals(posBot2)){
			return true;
		}
		return false;
	}

	boolean atAlleyEnd(Pos pos){
		Pos posTop = new Pos(1, 1);			// Exit for cars 1-4
		Pos posBot1 = new Pos(10, 2);		// Exit for cars 5-8
		if(pos.equals(posTop) || pos.equals(posBot1)){
			return true;
		}
		return false;
	}

	boolean atBarrier(Pos pos){
		Pos pos0 = new Pos(4,3);
		Pos pos1 = new Pos(4,4);
		Pos pos2 = new Pos(4,5);
		Pos pos3 = new Pos(4,6);
		Pos pos4 = new Pos(4,7);
		Pos pos5 = new Pos(5,8);
		Pos pos6 = new Pos(5,9);
		Pos pos7 = new Pos(5,10);
		Pos pos8 = new Pos(5,11);
		Pos[] positions = {pos0, pos1, pos2, pos3, pos4, pos5, pos6, pos7, pos8};

		for(int i = 0; i < positions.length; i++){
			if(pos.equals(positions[i])){
				barrier.setShutdownOn(true);
				return true;
			}
		}
		barrier.setShutdownOn(false);
		return false;
	}

	public void run() {
		try {

			speed = chooseSpeed();
			curpos = startpos;
			cd.mark(curpos,col,no);

			while (true) { 
				sleep(speed());

				if (atGate(curpos)) { 
					mygate.pass(); 
					speed = chooseSpeed();
				}
				if(inAlley(curpos)){
					alley.enter(no);
				}

				if(atAlleyEnd(curpos)){
					alley.leave(no);
				}

				if(atBarrier(curpos)){
					if(barrier.getBarrierOn()){
						barrier.sync(curpos);
					}else if(barrier.getShutdownOn()){
						barrier.shutdown();
					}
				}

				newpos = nextPos(curpos);
				try {
					semaphores[newpos.col][newpos.row].P();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				//  Move to new position
				if(!newpos.equals(curpos)){
					cd.clear(curpos);
					cd.mark(curpos,newpos,col,no);
					sleep(speed());	
					cd.clear(curpos,newpos);
					cd.mark(newpos,col,no);
				}
				semaphores[curpos.col][curpos.row].V();
				curpos = newpos;
			}

		} catch (Exception e) {
			cd.println("Exception in Car no. " + no);
			System.err.println("Exception in Car no. " + no + ":" + e);
			e.printStackTrace();
		}
	}
}

public class CarControl_step4 implements CarControlI{

	CarDisplayI cd;           						  // Reference to GUI
	Car[]  car;               						  // Cars
	Gate[] gate;              						  // Gates
	Semaphore[][] semaphores = new Semaphore[12][11]; // Array of semaphores to keep track of cars
	Alley alley = new Alley();
	Barrier barrier = new Barrier();

	public CarControl_step4(CarDisplayI cd) {

		for(Semaphore[] row : semaphores){
			for(int i = 0; i < row.length; i++){
				Semaphore f = new Semaphore(1);
				row[i] = f;
			}
		}

		this.cd = cd;
		car  = new  Car[9];
		gate = new Gate[9];

		for (int no = 0; no < 9; no++) {
			gate[no] = new Gate();
			car[no] = new Car(no,cd,gate[no],semaphores,alley, barrier);
			car[no].start();
		} 
	}

	public void startCar(int no) {
		gate[no].open();
	}

	public void stopCar(int no) {
		gate[no].close();
	}

	public void barrierOn() { 
		if(barrier.getBarrierOn()){
			cd.println("Barrier already on");
		} else{
			barrier.on();
		}
	}

	public void barrierOff() { 
		if(!barrier.getBarrierOn()){
			cd.println("Barrier already off");
		} else{
			barrier.off();
		}
	}

	public void barrierShutDown() { 
		barrier.shutdown();
		// This sleep is for illustrating how blocking affects the GUI
		// Remove when shutdown is implemented.
		try { Thread.sleep(3000); } catch (InterruptedException e) { }
		// Recommendation: 
		//   If not implemented call barrier.off() instead to make graphics consistent
	}

	public void setLimit(int k) { 
		cd.println("Setting of bridge limit not implemented in this version");
	}

	public void removeCar(int no) { 
		cd.println("Remove Car not implemented in this version");
	}

	public void restoreCar(int no) { 
		cd.println("Restore Car not implemented in this version");
	}

	/* Speed settings for testing purposes */

	public void setSpeed(int no, int speed) { 
		car[no].setSpeed(speed);
	}

	public void setVariation(int no, int var) { 
		car[no].setVariation(var);
	}
}






