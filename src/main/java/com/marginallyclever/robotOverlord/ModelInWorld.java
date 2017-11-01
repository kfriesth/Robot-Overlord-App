package com.marginallyclever.robotOverlord;

import java.util.ArrayList;

import javax.swing.JPanel;
import javax.vecmath.Vector3f;

import com.jogamp.opengl.GL2;
import com.marginallyclever.robotOverlord.model.Model;
import com.marginallyclever.robotOverlord.model.ModelFactory;


public class ModelInWorld extends Entity {
	/**
	 * 
	 */
	private static final long serialVersionUID = 180224086839215506L;
	
	protected String filename = null;
	protected transient Model model;
	protected transient ModelInWorldPanel modelPanel;
	
	// model render scale
	protected float scaleX=1, scaleY=1, scaleZ=1;
	
	
	public ModelInWorld() {}

	
	public String getFilename() {
		return filename;
	}
	
	
	public ArrayList<JPanel> getContextPanel(RobotOverlord gui) {
		ArrayList<JPanel> list = super.getContextPanel(gui);
		if(list==null) list = new ArrayList<JPanel>();
		
		modelPanel = new ModelInWorldPanel(gui,this);
		list.add(modelPanel);

		return list;
	}


	public void setFilename(String newFilename) {
		// if the filename has changed, throw out the model so it will be reloaded.
		if( this.filename != newFilename ) {
			this.filename = newFilename;
			model=null;
		}
	}

	public void setScaleX(float arg0) {		scaleX=arg0;	}
	public void setScaleY(float arg0) {		scaleY=arg0;	}
	public void setScaleZ(float arg0) {		scaleZ=arg0;	}
	public float getScaleX() {		return scaleX;	}
	public float getScaleY() {		return scaleY;	}
	public float getScaleZ() {		return scaleZ;	}
	public Vector3f getScale() {	return new Vector3f(scaleX,scaleY,scaleZ);	}
	public void setScale(Vector3f arg0) {
		scaleX=arg0.x;
		scaleY=arg0.y;
		scaleZ=arg0.z;
	}
	
	@Override
	public String getDisplayName() {
		return "Model";
	}
	
	
	public void render(GL2 gl2) {
		if( model==null && filename != null ) {
			try {
				model = ModelFactory.createModelFromFilename(filename);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}

		Vector3f p = getPosition();
		
		gl2.glPushMatrix();
			material.render(gl2);
		
			gl2.glTranslatef(p.x, p.y, p.z);
			gl2.glScalef(scaleX, scaleY, scaleZ);
			gl2.glEnable(gl2.GL_NORMALIZE);
			
			if( model==null ) {
				// draw placeholder
				final float size=10;
				gl2.glColor3f(1, 0, 0);
				PrimitiveSolids.drawBox(gl2,  size, 0.1f, 0.1f);
				gl2.glColor3f(0, 1, 0);
				PrimitiveSolids.drawBox(gl2,   0.1f,size, 0.1f);
				gl2.glColor3f(0, 0, 1);
				gl2.glPushMatrix();
				gl2.glTranslatef(0, 0, -size/2);
				PrimitiveSolids.drawBox(gl2,   0.1f, 0.1f,size);
				gl2.glPopMatrix();
			} else {
				model.render(gl2);
			}
		gl2.glPopMatrix();
	}
}
