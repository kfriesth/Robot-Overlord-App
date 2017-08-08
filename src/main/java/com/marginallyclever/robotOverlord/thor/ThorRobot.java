package com.marginallyclever.robotOverlord.thor;

import javax.swing.JPanel;
import javax.vecmath.Vector3f;
import com.jogamp.opengl.GL2;
import com.marginallyclever.communications.NetworkConnection;
import com.marginallyclever.convenience.MathHelper;
import com.marginallyclever.robotOverlord.*;
import com.marginallyclever.robotOverlord.arm5.tool.*;
import com.marginallyclever.robotOverlord.model.Model;
import com.marginallyclever.robotOverlord.model.ModelFactory;
import com.marginallyclever.robotOverlord.robot.Robot;
import com.marginallyclever.robotOverlord.thor.tool.ThorTool;
import com.marginallyclever.robotOverlord.thor.tool.ThorToolGripper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;


public class ThorRobot
extends Robot {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3644731265897692399L;
	// machine ID
	protected long robotUID;
	protected final static String hello = "HELLO WORLD! I AM THOR #";
	public final static String ROBOT_NAME = "Thor Arm";
	
	// machine dimensions from design software
	/**
    Base
        Global position (X,Y,Z): 0,0,0
        Rotation axis: None
        Rotation axis local position: None
    Articulation 1
        Global position (X,Y,Z): 0,0,49
        Rotation axis: Z
        Rotation axis local position: 0,0,0
    Articulation 2
        Global position (X,Y,Z): 0,0,137
        Rotation axis: Y
        Rotation axis local position: 0,0,65
    Articulation 3
        Global position (X,Y,Z): 0,0,295
        Rotation axis: Y
        Rotation axis local position: 0,0,64
    Articulation 4
        Global position (X,Y,Z): 0,0,377,5
        Rotation axis: Z
        Rotation axis local position: 0,0,0
    Articulation 5&6
        Global position (X,Y,Z): 0,0,510
        Art 5 Rotation axis: Y
        Art 5 Rotation axis local position: 0,0,47
        Art 6 Rotation axis: Z
        Art 6 Rotation axis local position: 0,0,0
	 */
	public final static double ANCHOR_TO_SHOULDER = 4.9;
	public final static double SHOULDER_TO_BICEP = 13.7-6.5;
	public final static double BICEP_TO_ELBOW = 29.5-6.4;
	public final static double ELBOW_TO_ULNA = 37.7-0.5;
	public final static double ULNA_TO_WRIST = 51.0-4.7;
	public final static float WRIST_TO_TOOL = 0;
	
	// model files
	private transient Model anchorModel = null;
	private transient Model shoulderModel = null;
	private transient Model bicepModel = null;
	private transient Model elbowModel = null;
	private transient Model ulnaModel = null;
	private transient Model wristModel = null;
	private transient Model handModel = null;

	private Material matAnchor		= new Material();
	private Material matShoulder	= new Material();
	private Material matBicep		= new Material();
	private Material matElbow		= new Material();
	private Material matUlna		= new Material();
	private Material matWrist		= new Material();
	private Material matHand		= new Material();
	
	// currently attached tool
	private ThorTool tool = null;
	
	// collision volumes
	Cylinder [] volumes = new Cylinder[6];

	// motion states
	protected ThorKeyframe motionNow = new ThorKeyframe();
	protected ThorKeyframe motionFuture = new ThorKeyframe();
	
	// keyboard history
	protected float aDir = 0.0f;
	protected float bDir = 0.0f;
	protected float cDir = 0.0f;
	protected float dDir = 0.0f;
	protected float eDir = 0.0f;

	protected float xDir = 0.0f;
	protected float yDir = 0.0f;
	protected float zDir = 0.0f;

	// machine logic states
	protected boolean armMoved = false;
	protected boolean isPortConfirmed=false;
	protected double speed=2;

	// visual debugging
	protected boolean isRenderFKOn=false;
	protected boolean isRenderIKOn=false;

	// gui
	protected transient ThorControlPanel arm5Panel=null;
	
	
	public ThorRobot() {
		super();
		
		setDisplayName(ROBOT_NAME);
		
		// set up bounding volumes
		for(int i=0;i<volumes.length;++i) {
			volumes[i] = new Cylinder();
		}
		volumes[0].setRadius(3.2f);
		volumes[1].setRadius(3.0f*0.575f);
		volumes[2].setRadius(2.2f);
		volumes[3].setRadius(1.15f);
		volumes[4].setRadius(1.2f);
		volumes[5].setRadius(1.0f*0.575f);
		
		rotateBase(0,0);
		checkAngleLimits(motionNow);
		checkAngleLimits(motionFuture);
		forwardKinematics(motionNow);
		forwardKinematics(motionFuture);
		inverseKinematics(motionNow);
		inverseKinematics(motionFuture);

		matAnchor  .setDiffuseColor(0,0,0,1);
		matShoulder.setDiffuseColor(1,0,0,1);
		matBicep   .setDiffuseColor(0,0,1,1);
		matElbow   .setDiffuseColor(1,0,1,1);
		matUlna    .setDiffuseColor(1,1,0,1);
		matWrist   .setDiffuseColor(0,1,0,1);
		matHand    .setDiffuseColor(0,1,1,1);
		
		tool = new ThorToolGripper();
		tool.attachTo(this);
	}
	

	@Override
	protected void loadModels(GL2 gl2) {
		try {
			anchorModel = ModelFactory.createModelFromFilename("/Thor/BaseThor.stl",0.1f);
			shoulderModel = ModelFactory.createModelFromFilename("/Thor/Art1Thor.stl",0.1f);
			bicepModel = ModelFactory.createModelFromFilename("/Thor/Art2Thor.stl",0.1f);
			elbowModel = ModelFactory.createModelFromFilename("/Thor/Art3Thor.stl",0.1f);
			ulnaModel = ModelFactory.createModelFromFilename("/Thor/Art4Thor.stl",0.1f);
			wristModel = ModelFactory.createModelFromFilename("/Thor/Art5.stl",0.1f);
			handModel = ModelFactory.createModelFromFilename("/Thor/Art6.stl",0.1f);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

    private void readObject(ObjectInputStream inputStream)
            throws IOException, ClassNotFoundException
    {
        inputStream.defaultReadObject();
    }

	
	@Override
	public ArrayList<JPanel> getContextPanel(RobotOverlord gui) {
		ArrayList<JPanel> list = super.getContextPanel(gui);
		
		if(list==null) list = new ArrayList<JPanel>();
		
		arm5Panel = new ThorControlPanel(gui,this);
		list.add(arm5Panel);
		updateGUI();

		ArrayList<JPanel> toolList = tool.getContextPanel(gui);
		Iterator<JPanel> iter = toolList.iterator();
		while(iter.hasNext()) {
			list.add(iter.next());
		}
		
		return list;
	}
	
	
	public boolean isPortConfirmed() {
		return isPortConfirmed;
	}
	
	
	private void enableFK() {		
		xDir=0;
		yDir=0;
		zDir=0;
	}
	
	private void disableFK() {	
		aDir=0;
		bDir=0;
		cDir=0;
		dDir=0;
		eDir=0;
	}

	public void setSpeed(double newSpeed) {
		speed=newSpeed;
	}
	public double getSpeed() {
		return speed;
	}
	
	public void moveA(float dir) {
		aDir=dir;
		enableFK();
	}

	public void moveB(float dir) {
		bDir=dir;
		enableFK();
	}

	public void moveC(float dir) {
		cDir=dir;
		enableFK();
	}

	public void moveD(float dir) {
		dDir=dir;
		enableFK();
	}

	public void moveE(float dir) {
		eDir=dir;
		enableFK();
	}

	public void moveX(float dir) {
		xDir=dir;
		disableFK();
	}

	public void moveY(float dir) {
		yDir=dir;
		disableFK();
	}

	public void moveZ(float dir) {
		zDir=dir;
		disableFK();
	}

	
	
	/**
	 * update the desired finger location
	 * @param delta time since the last update.  usually ~1/30s.
	 */
	protected void updateIK(float delta) {
		boolean changed=false;
		motionFuture.fingerPosition.set(motionNow.fingerPosition);
		final float vel=(float)speed;
		float dp = vel;// * delta;

		float dX=motionFuture.fingerPosition.x;
		float dY=motionFuture.fingerPosition.y;
		float dZ=motionFuture.fingerPosition.z;
		
		if (xDir!=0) {
			dX += xDir * dp;
			changed=true;
			xDir=0;
		}		
		if (yDir!=0) {
			dY += yDir * dp;
			changed=true;
			yDir=0;
		}
		if (zDir!=0) {
			dZ += zDir * dp;
			changed=true;
			zDir=0;
		}

		// rotations
		float ru=0,rv=0,rw=0;
		//if(uDown) rw= 0.1f;
		//if(jDown) rw=-0.1f;
		//if(aPos) rv=0.1f;
		//if(aNeg) rv=-0.1f;
		//if(bPos) ru=0.1f;
		//if(bNeg) ru=-0.1f;

		if(rw!=0 || rv!=0 || ru!=0 )
		{
			// On a 3-axis robot when homed the forward axis of the finger tip is pointing downward.
			// More complex arms start from the same assumption.
			Vector3f forward = new Vector3f(0,0,1);
			Vector3f right = new Vector3f(1,0,0);
			Vector3f up = new Vector3f();
			
			up.cross(forward,right);
			
			Vector3f of = new Vector3f(forward);
			Vector3f or = new Vector3f(right);
			Vector3f ou = new Vector3f(up);
			
			motionFuture.iku+=ru*dp;
			motionFuture.ikv+=rv*dp;
			motionFuture.ikw+=rw*dp;
			
			Vector3f result;

			result = MathHelper.rotateAroundAxis(forward,of,motionFuture.iku);  // TODO rotating around itself has no effect.
			result = MathHelper.rotateAroundAxis(result,or,motionFuture.ikv);
			result = MathHelper.rotateAroundAxis(result,ou,motionFuture.ikw);
			motionFuture.fingerForward.set(result);

			result = MathHelper.rotateAroundAxis(right,of,motionFuture.iku);
			result = MathHelper.rotateAroundAxis(result,or,motionFuture.ikv);
			result = MathHelper.rotateAroundAxis(result,ou,motionFuture.ikw);
			motionFuture.fingerRight.set(result);
			
			//changed=true;
		}
		
		//if(changed==true && motionFuture.movePermitted()) {
		if(changed) {
			motionFuture.fingerPosition.x = dX;
			motionFuture.fingerPosition.y = dY;
			motionFuture.fingerPosition.z = dZ;
			if(!inverseKinematics(motionFuture)) return;
			if(checkAngleLimits(motionFuture)) {
			//if(motionNow.fingerPosition.epsilonEquals(motionFuture.fingerPosition,0.1f) == false) {
				armMoved=true;
				isRenderIKOn=true;
				isRenderFKOn=false;

				sendChangeToRealMachine();
				if(!this.isPortConfirmed()) {
					// live data from the sensors will update motionNow, so only do this if we're unconnected.
					motionNow.set(motionFuture);
				}
				updateGUI();
			} else {
				motionFuture.set(motionNow);
			}
		}
	}
	
	
	protected void updateFK(float delta) {
		boolean changed=false;
		float velcd=(float)speed; // * delta
		float velabe=(float)speed; // * delta

		motionFuture.set(motionNow);
		
		float dE = motionFuture.angleE;
		float dD = motionFuture.angleD;
		float dC = motionFuture.angleC;
		float dB = motionFuture.angleB;
		float dA = motionFuture.angleA;

		if (eDir!=0) {
			dE += velabe * eDir;
			changed=true;
			eDir=0;
		}
		
		if (dDir!=0) {
			dD += velcd * dDir;
			changed=true;
			dDir=0;
		}

		if (cDir!=0) {
			dC += velcd * cDir;
			changed=true;
			cDir=0;
		}
		
		if(bDir!=0) {
			dB += velabe * bDir;
			changed=true;
			bDir=0;
		}
		
		if(aDir!=0) {
			dA += velabe * aDir;
			changed=true;
			aDir=0;
		}
		

		if(changed) {
			motionFuture.angleA=dA;
			motionFuture.angleB=dB;
			motionFuture.angleC=dC;
			motionFuture.angleD=dD;
			motionFuture.angleE=dE;
			if(checkAngleLimits(motionFuture)) {
				forwardKinematics(motionFuture);
				isRenderIKOn=false;
				isRenderFKOn=true;
				armMoved=true;
				
				sendChangeToRealMachine();
				if(!this.isPortConfirmed()) {
					// live data from the sensors will update motionNow, so only do this if we're unconnected.
					motionNow.set(motionFuture);
				}
				updateGUI();
			} else {
				motionFuture.set(motionNow);
			}
		}
	}

	
	protected float roundOff(float v) {
		float SCALE = 1000.0f;
		
		return Math.round(v*SCALE)/SCALE;
	}
	

	
	public void updateGUI() {
		Vector3f v = new Vector3f();
		v.set(motionNow.fingerPosition);
		// TODO rotate fingerPosition before adding position
		v.add(getPosition());
		arm5Panel.xPos.setText(Float.toString(roundOff(v.x)));
		arm5Panel.yPos.setText(Float.toString(roundOff(v.y)));
		arm5Panel.zPos.setText(Float.toString(roundOff(v.z)));

		arm5Panel.a1.setText(Float.toString(roundOff(motionNow.angleA)));
		arm5Panel.b1.setText(Float.toString(roundOff(motionNow.angleB)));
		arm5Panel.c1.setText(Float.toString(roundOff(motionNow.angleC)));
		arm5Panel.d1.setText(Float.toString(roundOff(motionNow.angleD)));
		arm5Panel.e1.setText(Float.toString(roundOff(motionNow.angleE)));
		
		arm5Panel.a2.setText(Float.toString(roundOff(motionNow.ik_angleA)));
		arm5Panel.b2.setText(Float.toString(roundOff(motionNow.ik_angleB)));
		arm5Panel.c2.setText(Float.toString(roundOff(motionNow.ik_angleC)));
		arm5Panel.d2.setText(Float.toString(roundOff(motionNow.ik_angleD)));
		arm5Panel.e2.setText(Float.toString(roundOff(motionNow.ik_angleE)));

		if( tool != null ) tool.updateGUI();
	}
	
	
	protected void sendChangeToRealMachine() {
		if(!isPortConfirmed) return;
		
		
		String str="";
		if(motionFuture.angleA!=motionNow.angleA) {
			str+=" A"+roundOff(motionFuture.angleA);
		}
		if(motionFuture.angleB!=motionNow.angleB) {
			str+=" B"+roundOff(motionFuture.angleB);
		}
		if(motionFuture.angleC!=motionNow.angleC) {
			str+=" C"+roundOff(motionFuture.angleC);
		}
		if(motionFuture.angleD!=motionNow.angleD) {
			str+=" D"+roundOff(motionFuture.angleD);
		}
		if(motionFuture.angleE!=motionNow.angleE) {
			str+=" E"+roundOff(motionFuture.angleE);
		}
		
		if(str.length()>0) {
			this.sendLineToRobot("R0"+str);
		}
	}
	
	@Override
	public void prepareMove(float delta) {
		updateIK(delta);
		updateFK(delta);
		if(tool != null) tool.update(delta);
	}

	@Override
	public void finalizeMove() {
		// copy motion_future to motion_now
		motionNow.set(motionFuture);
		
		if(armMoved) {
			if( this.isReadyToReceive ) {
				armMoved=false;
			}
		}
	}
	
	
	public void render(GL2 gl2) {
		super.render(gl2);
		
		gl2.glPushMatrix();
			// TODO rotate model
			
			gl2.glPushMatrix();
				Vector3f p = getPosition();
				gl2.glTranslatef(p.x, p.y, p.z);
				renderModels(gl2);
			gl2.glPopMatrix();

			if(isRenderFKOn) {
				gl2.glPushMatrix();
				gl2.glDisable(GL2.GL_DEPTH_TEST);
				renderFK(gl2);
				gl2.glEnable(GL2.GL_DEPTH_TEST);
				gl2.glPopMatrix();
			}
			
			if(isRenderIKOn) {
				gl2.glPushMatrix();
				gl2.glDisable(GL2.GL_DEPTH_TEST);
				renderIK(gl2);
				gl2.glEnable(GL2.GL_DEPTH_TEST);
				gl2.glPopMatrix();
			}
		gl2.glPopMatrix();
	}
	

	/**
	 * Visualize the inverse kinematics calculations
	 * @param gl2 openGL render context
	 */
	protected void renderIK(GL2 gl2) {
		boolean lightOn= gl2.glIsEnabled(GL2.GL_LIGHTING);
		boolean matCoOn= gl2.glIsEnabled(GL2.GL_COLOR_MATERIAL);
		gl2.glDisable(GL2.GL_LIGHTING);
		
		Vector3f ff = new Vector3f();
		ff.set(motionNow.fingerPosition);
		ff.add(motionNow.fingerForward);
		Vector3f fr = new Vector3f();
		fr.set(motionNow.fingerPosition);
		fr.add(motionNow.fingerRight);
		
		
		gl2.glColor4f(1,0,0,1);

		gl2.glBegin(GL2.GL_LINE_STRIP);
		gl2.glVertex3d(0,0,0);
		gl2.glVertex3d(motionNow.ik_shoulder.x,motionNow.ik_shoulder.y,motionNow.ik_shoulder.z);
		gl2.glVertex3d(motionNow.ik_boom.x,motionNow.ik_boom.y,motionNow.ik_boom.z);
		gl2.glVertex3d(motionNow.ik_elbow.x,motionNow.ik_elbow.y,motionNow.ik_elbow.z);
		gl2.glVertex3d(motionNow.ik_wrist.x,motionNow.ik_wrist.y,motionNow.ik_wrist.z);
		gl2.glVertex3d(motionNow.fingerPosition.x,motionNow.fingerPosition.y,motionNow.fingerPosition.z);
		gl2.glVertex3d(ff.x,ff.y,ff.z);		
		gl2.glVertex3d(motionNow.fingerPosition.x,motionNow.fingerPosition.y,motionNow.fingerPosition.z);
		gl2.glVertex3d(fr.x,fr.y,fr.z);
		gl2.glEnd();

		// finger tip
		gl2.glColor4f(1,0.8f,0,1);
		PrimitiveSolids.drawStar(gl2, motionNow.fingerPosition );
		PrimitiveSolids.drawStar(gl2, ff );
		PrimitiveSolids.drawStar(gl2, fr );
	
		if(lightOn) gl2.glEnable(GL2.GL_LIGHTING);
		if(matCoOn) gl2.glEnable(GL2.GL_COLOR_MATERIAL);
	}
	
	
	/**
	 * Draw the arm without calling glRotate to prove forward kinematics are correct.
	 * @param gl2 openGL render context
	 */
	protected void renderFK(GL2 gl2) {
		boolean lightOn= gl2.glIsEnabled(GL2.GL_LIGHTING);
		boolean matCoOn= gl2.glIsEnabled(GL2.GL_COLOR_MATERIAL);
		gl2.glDisable(GL2.GL_LIGHTING);

		Vector3f ff = new Vector3f();
		ff.set(motionNow.fingerPosition);
		ff.add(motionNow.fingerForward);
		Vector3f fr = new Vector3f();
		fr.set(motionNow.fingerPosition);
		fr.add(motionNow.fingerRight);
		
		gl2.glColor4f(1,1,1,1);
		gl2.glBegin(GL2.GL_LINE_STRIP);
		
		gl2.glVertex3d(0,0,0);
		gl2.glVertex3d(motionNow.shoulder.x,motionNow.shoulder.y,motionNow.shoulder.z);
		gl2.glVertex3d(motionNow.boom.x,motionNow.boom.y,motionNow.boom.z);
		gl2.glVertex3d(motionNow.elbow.x,motionNow.elbow.y,motionNow.elbow.z);
		gl2.glVertex3d(motionNow.wrist.x,motionNow.wrist.y,motionNow.wrist.z);
		gl2.glVertex3d(motionNow.fingerPosition.x,motionNow.fingerPosition.y,motionNow.fingerPosition.z);
		gl2.glVertex3d(ff.x,ff.y,ff.z);
		gl2.glVertex3d(motionNow.fingerPosition.x,motionNow.fingerPosition.y,motionNow.fingerPosition.z);
		gl2.glVertex3d(fr.x,fr.y,fr.z);

		gl2.glEnd();

		// finger tip
		gl2.glColor4f(1,0.8f,0,1);		PrimitiveSolids.drawStar(gl2, motionNow.fingerPosition );
		gl2.glColor4f(0,0.8f,1,1);		PrimitiveSolids.drawStar(gl2, ff );
		gl2.glColor4f(0,0,1,1);			PrimitiveSolids.drawStar(gl2, fr );
	
		if(lightOn) gl2.glEnable(GL2.GL_LIGHTING);
		if(matCoOn) gl2.glEnable(GL2.GL_COLOR_MATERIAL);
	}
	
	
	/**
	 * Draw the physical model according to the angle values in the motionNow state.
	 * @param gl2 openGL render context
	 */
	protected void renderModels(GL2 gl2) {
		// anchor
		matAnchor.render(gl2);
		// this rotation is here because the anchor model was built facing the wrong way.
		
		anchorModel.render(gl2);

		// shoulder (E)
		matShoulder.render(gl2);
		gl2.glTranslated(0, 0, ANCHOR_TO_SHOULDER);
		//gl2.glRotated(motionNow.angleE,0,0,1);
		shoulderModel.render(gl2);

		// shoulder pinion
		gl2.glTranslated(0, 0, SHOULDER_TO_BICEP);
		//gl2.glRotated(motionNow.angleE*anchor_gear_ratio,0,1,0);
		matBicep.render(gl2);
		bicepModel.render(gl2);

		// boom (D)
		gl2.glTranslated(0,0,BICEP_TO_ELBOW);
		//gl2.glRotated(90-motionNow.angleD,0,0,1);
		matElbow.render(gl2);
		elbowModel.render(gl2);

		// stick (C)
		matUlna.render(gl2);
		gl2.glTranslated(0,0,ELBOW_TO_ULNA);
		//gl2.glRotated(90+motionNow.angleC,0,0,1);
		ulnaModel.render(gl2);

		//gl2.glRotated(-motionNow.angleB+180,0,0,1);

		gl2.glTranslated(0,0,ULNA_TO_WRIST);
		matWrist.render(gl2);
		wristModel.render(gl2);
			
		// tool holder
		matHand.render(gl2);
		handModel.render(gl2);
			
		gl2.glTranslated(0,0,WRIST_TO_TOOL);
		//gl2.glTranslated(-6, 0, 0);
		if(tool!=null) {
			tool.render(gl2);
		}

		gl2.glPopMatrix();  // wrist
	}
	
	
	protected void drawMatrix(GL2 gl2,Vector3f p,Vector3f u,Vector3f v,Vector3f w) {
		drawMatrix(gl2,p,u,v,w,1);
	}
	
	
	protected void drawMatrix(GL2 gl2,Vector3f p,Vector3f u,Vector3f v,Vector3f w,float scale) {
		gl2.glPushMatrix();
		gl2.glDisable(GL2.GL_DEPTH_TEST);
		gl2.glTranslatef(p.x, p.y, p.z);
		gl2.glScalef(scale, scale, scale);
		
		gl2.glBegin(GL2.GL_LINES);
		gl2.glColor3f(1,1,0);		gl2.glVertex3f(0,0,0);		gl2.glVertex3f(u.x,u.y,u.z);
		gl2.glColor3f(0,1,1);		gl2.glVertex3f(0,0,0);		gl2.glVertex3f(v.x,v.y,v.z);
		gl2.glColor3f(1,0,1);		gl2.glVertex3f(0,0,0);		gl2.glVertex3f(w.x,w.y,w.z);
		gl2.glEnd();
		
		gl2.glEnable(GL2.GL_DEPTH_TEST);
		gl2.glPopMatrix();
	}
	
	
	protected void drawBounds(GL2 gl2) {
		throw new UnsupportedOperationException();
	}
	
	
	
	private double parseNumber(String str) {
		float f=0;
		try {
			f = Float.parseFloat(str);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		return f;
	}
	

	public void setModeAbsolute() {
		if(connection!=null) this.sendLineToRobot("G90");
	}
	
	
	public void setModeRelative() {
		if(connection!=null) this.sendLineToRobot("G91");
	}
	
	
	@Override
	// override this method to check that the software is connected to the right type of robot.
	public void dataAvailable(NetworkConnection arg0,String line) {
		if(line.contains(hello)) {
			isPortConfirmed=true;
			//finalizeMove();
			setModeAbsolute();
			this.sendLineToRobot("R1");
			
			String uidString=line.substring(hello.length()).trim();
			System.out.println(">>> UID="+uidString);
			try {
				long uid = Long.parseLong(uidString);
				if(uid==0) {
					robotUID = getNewRobotUID();
				} else {
					robotUID = uid;
				}
				arm5Panel.setUID(robotUID);
			}
			catch(Exception e) {
				e.printStackTrace();
			}

			setDisplayName(ROBOT_NAME+" #"+robotUID);
		}
		
		if( isPortConfirmed ) {
			if(line.startsWith("A")) {
				String items[] = line.split(" ");
				if(items.length>=5) {
					for(int i=0;i<items.length;++i) {
						if(items[i].startsWith("A")) {
							float v = (float)parseNumber(items[i].substring(1));
							if(motionFuture.angleA != v) {
								motionFuture.angleA = v;
								arm5Panel.a1.setText(Float.toString(roundOff(v)));
							}
						} else if(items[i].startsWith("B")) {
							float v = (float)parseNumber(items[i].substring(1));
							if(motionFuture.angleB != v) {
								motionFuture.angleB = v;
								arm5Panel.b1.setText(Float.toString(roundOff(v)));
							}
						} else if(items[i].startsWith("C")) {
							float v = (float)parseNumber(items[i].substring(1));
							if(motionFuture.angleC != v) {
								motionFuture.angleC = v;
								arm5Panel.c1.setText(Float.toString(roundOff(v)));
							}
						} else if(items[i].startsWith("D")) {
							float v = (float)parseNumber(items[i].substring(1));
							if(motionFuture.angleD != v) {
								motionFuture.angleD = v;
								arm5Panel.d1.setText(Float.toString(roundOff(v)));
							}
						} else if(items[i].startsWith("E")) {
							float v = (float)parseNumber(items[i].substring(1));
							if(motionFuture.angleE != v) {
								motionFuture.angleE = v;
								arm5Panel.e1.setText(Float.toString(roundOff(v)));
							}
						}
					}
					
					forwardKinematics(motionFuture);
					motionNow.set(motionFuture);
					updateGUI();
				}
			} else {
				System.out.print("*** "+line);
			}
		}
	}
	

	public void moveBase(Vector3f dp) {
		motionFuture.anchorPosition.set(dp);
	}
	
	
	public void rotateBase(float pan,float tilt) {
		motionFuture.base_pan=pan;
		motionFuture.base_tilt=tilt;
		
		motionFuture.baseForward.y = (float)Math.sin(pan * Math.PI/180.0) * (float)Math.cos(tilt * Math.PI/180.0);
		motionFuture.baseForward.x = (float)Math.cos(pan * Math.PI/180.0) * (float)Math.cos(tilt * Math.PI/180.0);
		motionFuture.baseForward.z =                                        (float)Math.sin(tilt * Math.PI/180.0);
		motionFuture.baseForward.normalize();
		
		motionFuture.baseUp.set(0,0,1);
	
		motionFuture.baseRight.cross(motionFuture.baseForward, motionFuture.baseUp);
		motionFuture.baseRight.normalize();
		motionFuture.baseUp.cross(motionFuture.baseRight, motionFuture.baseForward);
		motionFuture.baseUp.normalize();
	}
	
	
	public BoundingVolume [] getBoundingVolumes() {
		// shoulder joint
		Vector3f t1=new Vector3f(motionFuture.baseRight);
		t1.scale(volumes[0].getRadius()/2);
		t1.add(motionFuture.shoulder);
		Vector3f t2=new Vector3f(motionFuture.baseRight);
		t2.scale(-volumes[0].getRadius()/2);
		t2.add(motionFuture.shoulder);
		volumes[0].SetP1(getWorldCoordinatesFor(t1));
		volumes[0].SetP2(getWorldCoordinatesFor(t2));
		// bicep
		volumes[1].SetP1(getWorldCoordinatesFor(motionFuture.shoulder));
		volumes[1].SetP2(getWorldCoordinatesFor(motionFuture.elbow));
		// elbow
		t1.set(motionFuture.baseRight);
		t1.scale(volumes[0].getRadius()/2);
		t1.add(motionFuture.elbow);
		t2.set(motionFuture.baseRight);
		t2.scale(-volumes[0].getRadius()/2);
		t2.add(motionFuture.elbow);
		volumes[2].SetP1(getWorldCoordinatesFor(t1));
		volumes[2].SetP2(getWorldCoordinatesFor(t2));
		// ulna
		volumes[3].SetP1(getWorldCoordinatesFor(motionFuture.elbow));
		volumes[3].SetP2(getWorldCoordinatesFor(motionFuture.wrist));
		// wrist
		t1.set(motionFuture.baseRight);
		t1.scale(volumes[0].getRadius()/2);
		t1.add(motionFuture.wrist);
		t2.set(motionFuture.baseRight);
		t2.scale(-volumes[0].getRadius()/2);
		t2.add(motionFuture.wrist);
		volumes[4].SetP1(getWorldCoordinatesFor(t1));
		volumes[4].SetP2(getWorldCoordinatesFor(t2));
		// finger
		volumes[5].SetP1(getWorldCoordinatesFor(motionFuture.wrist));
		volumes[5].SetP2(getWorldCoordinatesFor(motionFuture.fingerPosition));
		
		return volumes;
	}
	
	
	Vector3f getWorldCoordinatesFor(Vector3f in) {
		Vector3f out = new Vector3f(motionFuture.anchorPosition);
		
		Vector3f tempx = new Vector3f(motionFuture.baseForward);
		tempx.scale(in.x);
		out.add(tempx);

		Vector3f tempy = new Vector3f(motionFuture.baseRight);
		tempy.scale(-in.y);
		out.add(tempy);

		Vector3f tempz = new Vector3f(motionFuture.baseUp);
		tempz.scale(in.z);
		out.add(tempz);
				
		return out;
	}
	

	/**
	 * based on http://www.exampledepot.com/egs/java.net/Post.html
	 */
	private long getNewRobotUID() {
		long new_uid = 0;

		try {
			// Send data
			URL url = new URL("https://marginallyclever.com/evil_minion_getuid.php");
			URLConnection conn = url.openConnection();
			try (
                    final InputStream connectionInputStream = conn.getInputStream();
                    final Reader inputStreamReader = new InputStreamReader(connectionInputStream, StandardCharsets.UTF_8);
                    final BufferedReader rd = new BufferedReader(inputStreamReader)
					) {
				String line = rd.readLine();
				new_uid = Long.parseLong(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}

		// did read go ok?
		if (new_uid != 0) {
			// make sure a topLevelMachinesPreferenceNode node is created
			// tell the robot it's new UID.
			this.sendLineToRobot("UID " + new_uid);
		}
		return new_uid;
	}
	
	
	// TODO check for collisions with http://geomalgorithms.com/a07-_distance.html#dist3D_Segment_to_Segment ?
	public boolean movePermitted(ThorKeyframe keyframe) {
		// don't hit floor?
		// don't hit ceiling?

		// check far limit
		// seems doable
		if(!inverseKinematics(keyframe)) return false;
		// angle are good?
		if(!checkAngleLimits(keyframe)) return false;

		// OK
		return true;
	}
	
	
	protected boolean checkAngleLimits(ThorKeyframe keyframe) {
		// machine specific limits
		//a
		//if (angleA < -180) return false;
		//if (angleA >  180) return false;
		//b
		if (keyframe.angleB <      72.90) keyframe.angleB = 72.90f;
		if (keyframe.angleB >  360-72.90) keyframe.angleB = 360-72.90f;
		//c
		if (keyframe.angleC <   50.57) keyframe.angleC = 50.57f;
		if (keyframe.angleC >  160.31) keyframe.angleC = 160.31f;
		//d
		if (keyframe.angleD <   87.85) keyframe.angleD = 87.85f;
		if (keyframe.angleD >  173.60) keyframe.angleD = 173.60f;
		//e
		//if (angleE < 180-165) return false;
		//if (angleE > 180+165) return false;

		return true;
	}
	
	
	/**
	 * Find the arm joint angles that would put the finger at the desired location.
	 * @return false if successful, true if the IK solution cannot be found.
	 */
	protected boolean inverseKinematics(ThorKeyframe keyframe) {
		double aa,bb,cc,dd,ee;
		
		Vector3f v0 = new Vector3f();
		Vector3f v1 = new Vector3f();
		Vector3f v2 = new Vector3f();
		Vector3f planar = new Vector3f();
		Vector3f planeNormal = new Vector3f();
		Vector3f planeRight = new Vector3f(0,0,1);

		// Finger position is never on x=y=0 line, so this is safe.
		planar.set(keyframe.fingerPosition);
		planar.z=0;
		planar.normalize();
		planeNormal.set(-planar.y,planar.x,0);
		planeNormal.normalize();
		
		// Find E
		ee = Math.atan2(planar.y, planar.x);
		ee = MathHelper.capRotation(ee);
		keyframe.ik_angleE = (float)Math.toDegrees(ee);

		keyframe.ik_shoulder.set(0,0,0);
		keyframe.ik_boom.set((float)ThorRobot.SHOULDER_TO_BICEP*(float)Math.cos(ee),
							 (float)ThorRobot.SHOULDER_TO_BICEP*(float)Math.sin(ee),
							 (float)ThorRobot.BICEP_TO_ELBOW);
		keyframe.ik_boom.add(keyframe.ik_shoulder);
		
		// Find wrist 
		keyframe.ik_wrist.set(keyframe.fingerForward);
		keyframe.ik_wrist.scale(ThorRobot.WRIST_TO_TOOL);
		keyframe.ik_wrist.add(keyframe.fingerPosition);
				
		// Find elbow by using intersection of circles.
		// http://mathworld.wolfram.com/Circle-CircleIntersection.html
		// x = (dd-rr+RR) / (2d)
		v0.set(keyframe.ik_wrist);
		v0.sub(keyframe.ik_boom);
		float d = v0.length();
		float R = (float)Math.abs(ThorRobot.ELBOW_TO_ULNA);
		float r = (float)Math.abs(ThorRobot.ULNA_TO_WRIST);
		if( d > R+r ) {
			// impossibly far away
			return false;
		}/*
		if( d < Math.abs(R-r) ) {
			// impossibly close?
			return false;
		}*/
		
		float x = (d*d - r*r + R*R ) / (2*d);
		if( x > R ) {
			// would cause Math.sqrt(a negative number)
			return false;
		}
		v0.normalize();
		keyframe.ik_elbow.set(v0);
		keyframe.ik_elbow.scale(x);
		keyframe.ik_elbow.add(keyframe.ik_boom);
		// v1 is now at the intersection point between ik_wrist and ik_boom
		float a = (float)( Math.sqrt( R*R - x*x ) );
		v1.cross(planeNormal, v0);
		v1.scale(-a);
		keyframe.ik_elbow.add(v1);

		// find boom angle (D)
		v0.set(keyframe.ik_elbow);
		v0.sub(keyframe.ik_boom);
		x = -planar.dot(v0);
		float y = planeRight.dot(v0);
		dd = Math.atan2(y,x);
		dd = MathHelper.capRotation(dd);
		keyframe.ik_angleD = (float)Math.toDegrees(dd);
		
		// find elbow angle (C)
		planar.set(v0);
		planar.normalize();
		planeRight.cross(planeNormal,v0);
		planeRight.normalize();
		v0.set(keyframe.ik_wrist);
		v0.sub(keyframe.ik_elbow);
		x = -planar.dot(v0);
		y = planeRight.dot(v0);
		cc = Math.atan2(y,x);
		cc = MathHelper.capRotation(cc);
		keyframe.ik_angleC = (float)Math.toDegrees(cc);
		
		// find wrist angle (B)
		planar.set(keyframe.ik_wrist);
		planar.sub(keyframe.ik_elbow);
		planar.normalize();
		planeRight.cross(planeNormal,v0);
		planeRight.normalize();
		v0.set(keyframe.fingerPosition);
		v0.sub(keyframe.ik_wrist);
		x = -planar.dot(v0);
		y = -planeRight.dot(v0);
		bb = Math.atan2(y,x);
		bb = MathHelper.capRotation(bb);
		keyframe.ik_angleB = (float)Math.toDegrees(bb);
		
		// find wrist rotation (A)
		v0.set(keyframe.fingerPosition);
		v0.sub(keyframe.ik_wrist);
		v0.normalize();
		v1.set(planeNormal);
		v2.cross(planeNormal,v0);
		v0.set(keyframe.fingerRight);
		
		x = v2.dot(v0);
		y = -v1.dot(v0);
		aa = Math.atan2(y,x)-bb-Math.PI/2.0;
		aa = MathHelper.capRotation(aa);
		keyframe.ik_angleA = (float)Math.toDegrees(aa);
		
		keyframe.angleA=keyframe.ik_angleA;
		keyframe.angleB=keyframe.ik_angleB;
		keyframe.angleC=keyframe.ik_angleC;
		keyframe.angleD=keyframe.ik_angleD;
		keyframe.angleE=keyframe.ik_angleE;

		return true;
	}
	
	/**
	 * Calculate the finger location from the angles at each joint
	 * @param state
	 */
	protected void forwardKinematics(ThorKeyframe keyframe) {
		double e = Math.toRadians(keyframe.angleE);
		double d = Math.toRadians(180-keyframe.angleD);
		double c = Math.toRadians(keyframe.angleC+180);
		double b = Math.toRadians(180-keyframe.angleB);
		double a = Math.toRadians(keyframe.angleA);
		
		Vector3f v0 = new Vector3f(0,0,0);
		Vector3f v1 = new Vector3f((float)ThorRobot.SHOULDER_TO_BICEP*(float)Math.cos(e),
									(float)ThorRobot.SHOULDER_TO_BICEP*(float)Math.sin(e),
									(float)ThorRobot.BICEP_TO_ELBOW);
		Vector3f planar = new Vector3f((float)Math.cos(e),(float)Math.sin(e),0);
		planar.normalize();
		Vector3f planeNormal = new Vector3f(-v1.y,v1.x,0);
		planeNormal.normalize();
		Vector3f planarRight = new Vector3f();
		planarRight.cross(planar, planeNormal);
		planarRight.normalize();

		// anchor to shoulder
		keyframe.shoulder.set(v0);
		
		// shoulder to boom
		v1.add(v0);
		keyframe.boom.set(v1);
		
		// boom to elbow
		v0.set(v1);
		v1.set(planar);
		v1.scale( (float)( ThorRobot.ELBOW_TO_ULNA * Math.cos(d) ) );
		Vector3f v2 = new Vector3f();
		v2.set(planarRight);
		v2.scale( (float)( ThorRobot.ELBOW_TO_ULNA * Math.sin(d) ) );
		v1.add(v2);
		v1.add(v0);
		
		keyframe.elbow.set(v1);
		
		// elbow to wrist
		planar.set(v0);
		planar.sub(v1);
		planar.normalize();
		planarRight.cross(planar, planeNormal);
		planarRight.normalize();
		v0.set(v1);

		v1.set(planar);
		v1.scale( (float)( ThorRobot.ULNA_TO_WRIST * Math.cos(c) ) );
		v2.set(planarRight);
		v2.scale( (float)( ThorRobot.ULNA_TO_WRIST * Math.sin(c) ) );
		v1.add(v2);
		v1.add(v0);
		
		keyframe.wrist.set(v1);

		// wrist to finger
		planar.set(v0);
		planar.sub(v1);
		planar.normalize();
		planarRight.cross(planar, planeNormal);
		planarRight.normalize();
		v0.set(v1);

		v1.set(planar);
		v1.scale( (float)( ThorRobot.WRIST_TO_TOOL * Math.cos(b) ) );
		v2.set(planarRight);
		v2.scale( (float)( ThorRobot.WRIST_TO_TOOL * Math.sin(b) ) );
		v1.add(v2);
		v1.add(v0);

		keyframe.fingerPosition.set(v1);

		// finger rotation
		planarRight.set(planeNormal);
		planeNormal.set(v1);
		planeNormal.sub(v0);
		planeNormal.normalize();
		planar.cross(planeNormal,planarRight);
		v0.set(v1);

		v1.set(planar);
		v1.scale( (float)( ThorRobot.WRIST_TO_TOOL * Math.cos(a-b) ) );
		v2.set(planarRight);
		v2.scale( (float)( ThorRobot.WRIST_TO_TOOL * Math.sin(a-b) ) );
		v1.add(v2);
		v1.normalize();
		
		v0.set(keyframe.fingerPosition);
		v0.sub(keyframe.wrist);

		keyframe.fingerForward.set(planeNormal);
		
		keyframe.fingerRight.cross(v1, planeNormal);
		keyframe.fingerRight.normalize();
	}
}