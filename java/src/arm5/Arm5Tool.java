package arm5;

import javax.media.opengl.GL2;
import javax.vecmath.Vector3f;

/**
 * A tool that can be used by the Arm5
 * @author danroyer
 *
 */
public class Arm5Tool {
	/**
	 * The visible model of the tool.  The origin of the model should match the attachment point on the arm
	 */
	private Model model=null;
	
	/**
	 * An offset to a more friendly point of reference.  A drill's offset might be the bit tip. 
	 */
	private Vector3f offset;
	
	
	void render(GL2 gl2) {
		if(model!=null) model.render(gl2);
	}
}
