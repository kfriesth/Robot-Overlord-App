package com.marginallyclever.robotOverlord.entity.scene.robotEntity.dhRobotEntity;

import java.util.List;
import java.util.ArrayList;

import javax.vecmath.Matrix4d;

import com.marginallyclever.convenience.IntersectionTester;
import com.marginallyclever.robotOverlord.RobotOverlord;
import com.marginallyclever.robotOverlord.entity.Entity;
import com.marginallyclever.robotOverlord.entity.scene.PoseEntity;
import com.marginallyclever.robotOverlord.entity.scene.robotEntity.dhRobotEntity.DHLink.LinkAdjust;
import com.marginallyclever.robotOverlord.entity.scene.robotEntity.dhRobotEntity.dhTool.DHTool;
import com.marginallyclever.robotOverlord.entity.scene.robotEntity.dhRobotEntity.solvers.DHIKSolver;
import com.marginallyclever.robotOverlord.swingInterface.view.ViewPanel;

/**
 * A robot designed using D-H parameters.
 * 
 * @author Dan Royer
 */
public class DHRobotEntity extends PoseEntity {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8209132282893336655L;

	// a list of DHLinks describing the kinematic chain.
	public List<DHLink> links = new ArrayList<DHLink>();
	
	// The solver for this type of robot
	protected transient DHIKSolver solver;

	// a DHTool attached to the arm.
	public DHTool dhTool;

	public DHRobotEntity() {
		super();
		setName("DHRobot");
	}
	
	public DHRobotEntity(DHRobotEntity b) {
		this();
		set(b);
	}

	public void set(DHRobotEntity b) {
		super.set(b);
		// remove any exiting links from other robot to be certain.
		setNumLinks(b.getNumLinks());
		// copy my links to the next robot
		for(int i=0;i<b.getNumLinks();++i) {
			links.get(i).set(b.links.get(i));
		}
		
		solver = b.solver;
		dhTool = b.dhTool;
		
		refreshPose();
	}
	
	/**
	 * Override this method to return the correct solver for your type of robot.
	 * 
	 * @return the IK solver for a specific type of robot.
	 */
	public DHIKSolver getIKSolver() {
		return solver;
	}

	public void setIKSolver(DHIKSolver solver0) {
		solver = solver0;
	}

	public Matrix4d getParentMatrix() {
		if( parent == null || !(parent instanceof PoseEntity) ) {
			Matrix4d m = new Matrix4d();
			m.setIdentity();
			return m;
		} else {
			return ((PoseEntity)parent).getPose();
		}
	}
	
	/**
	 * Update the pose matrix of each DH link, then use forward kinematics to find
	 * the end position.
	 */
	public void refreshPose() {
		for( DHLink link : links ) {
			link.refreshPoseMatrix();
		}
		
		if (dhTool != null) {
			dhTool.refreshPoseMatrix();
		}
	}

	/**
	 * Adjust the number of links in this robot
	 * 
	 * @param newSize must be >= 0
	 */
	public void setNumLinks(int newSize) {
		if(newSize < 0) newSize = 0;

		links.clear();
		
		// count the number of existing children.
		Entity prev=this;
		boolean found;
		int s=0;
		while(prev.getChildren().size()>0 && s<newSize) {
			found=false;
			for( Entity c : prev.getChildren() ) {
				if(c instanceof DHLink ) {
					links.add((DHLink)c);
					prev=c;
					++s;
					found=true;
					break;
				}
			}
			// in case there are children but none are DHLinks
			if(found==false) break;
		}

		// if the number is too low, add more.
		while(s<newSize) {
			DHLink newLink = new DHLink();
			links.add(newLink);
			prev.addChild(newLink);
			prev = newLink;
			++s;
		}
		
		// if the number is too high, delete the remaining children.
		prev.getChildren().clear();
	}

	// the tool should be the child of the last link in the chain
	public void setTool(DHTool arg0) {
		removeTool();
		dhTool = arg0;
		links.get(links.size()-1).addChild(arg0);
	}

	public void removeTool() {
		if(dhTool==null) return;
		
		// the child of the last link in the chain is the tool
		links.get(links.size()-1).removeChild(dhTool);
		dhTool = null;
	}

	public DHTool getCurrentTool() {
		return dhTool;
	}

	/**
	 * checks that the keyframe is sane that no collisions occur.
	 * @param keyframe
	 * @return false if the keyframe is not sane or a collision occurs.
	 */
	public boolean sanityCheck(DHKeyframe keyframe) {
		if(!keyframeAnglesAreOK(keyframe))	return false;
		if(collidesWithSelf(keyframe))		return false;
		if(collidesWithWorld(keyframe))		return false;
		return true;
	}
		
	/**
	 * Test physical bounds of link N against all links &lt;N-1 and all links
	 * &gt;N+1 We're using separating Axis Theorem. See
	 * https://gamedev.stackexchange.com/questions/25397/obb-vs-obb-collision-detection
	 * 
	 * @param keyframe the angles at time of test
	 * @return true if there are no collisions
	 */
	public boolean collidesWithSelf(DHKeyframe futureKey) {
		DHKeyframe originalKey = solver.createDHKeyframe();
		getPoseFK(originalKey);
		// move the clone to the keyframe pose
		setPoseFK(futureKey);
		
		int size = links.size();
		for (int i = 0; i < size; ++i) {
			if (links.get(i).getModel() == null)
				continue;

			for (int j = i + 2; j < size; ++j) {
				if (links.get(j).getModel() == null)
					continue;

				if (IntersectionTester.cuboidCuboid(
						links.get(i).getCuboid(),
						links.get(j).getCuboid())) {
						System.out.println("Self collision between "+
									i+":"+links.get(i).getName()+" and "+
									j+":"+links.get(j).getName());

					setPoseFK(originalKey);
					return true;
				}
			}
		}

		setPoseFK(originalKey);
		return false;
	}

	/**
	 * Test physical bounds of all links with the world.
	 * We're using separating Axis Theorem. See https://gamedev.stackexchange.com/questions/25397/obb-vs-obb-collision-detection
	 * 
	 * @param keyframe the angles at time of test
	 * @return true if there are no collisions
	 */
	public boolean collidesWithWorld(DHKeyframe futureKey) {
		if( this.parent == null ) {
			return false;
		}
		
		// create a clone of the robot
		DHKeyframe originalKey = solver.createDHKeyframe();
		getPoseFK(originalKey);
		// move the clone to the keyframe pose
		setPoseFK(futureKey);
		boolean result = ((RobotOverlord)getRoot()).getWorld().collisionTest((PoseEntity)parent); 
		setPoseFK(originalKey);
		
		return result;
	}

	/**
	 * Perform a sanity check. Make sure the angles in the keyframe are within the
	 * joint range limits.
	 * 
	 * @param keyframe
	 * @return
	 */
	public boolean keyframeAnglesAreOK(DHKeyframe keyframe) {
		int j = 0;
		for( DHLink link : links ) {
			if(link.flags == LinkAdjust.NONE) continue;
			double v = keyframe.fkValues[j++];
			if (link.rangeMax.get() < v || link.rangeMin.get() > v) {
				System.out.println("FK "+ link.flags + j + ":" + v + " out (" + link.rangeMin.get() + " to " + link.rangeMax.get() + ")");
				return false;
			}
		}

		return true;
	}

	/**
	 * Find the forward kinematic pose of robot r that would give an end effector matrix matching m.
	 * If the FK pose is found, set the adjustable values of the links to said pose.
	 * @param m the matrix of the finger tip of the robot, relative to the base of the robot.
	 */
	public boolean setPoseIK(Matrix4d m) {
		DHKeyframe oldPose = solver.createDHKeyframe();
		getPoseFK(oldPose);
		DHKeyframe newPose = solver.createDHKeyframe();
		
		DHIKSolver.SolutionType s = solver.solveWithSuggestion(this, m, newPose,oldPose);
		if (s == DHIKSolver.SolutionType.ONE_SOLUTION) {
			if (sanityCheck(newPose)) {
				setPoseFK(newPose);
				return true;
			} else System.out.println("setPoseIK() insane");
		} else System.out.println("setPoseIK() impossible");
		setPoseFK(oldPose);
		return false;
	}

	/**
	 * Set the robot's FK values to the keyframe values and then refresh the pose.
	 * This method is used by others to verify for collisions, so this method
	 * cannot verify collisions itself.
	 * @param keyframe
	 */
	public void setPoseFK(DHKeyframe keyframe) {
		int stop=keyframe.fkValues.length;
		int j = 0;
		
		for( DHLink link : links ) {
			if(j==stop) break;
			if(link.hasAdjustableValue()) {
				link.setAdjustableValue(keyframe.fkValues[j++]);
			}
		}

		refreshPose();
	}

	/**
	 * Store the robot's FK values in the keyframe.
	 * 
	 * @param keyframe to set
	 */
	public void getPoseFK(DHKeyframe keyframe) {
		assert(keyframe.fkValues.length==links.size());

		int j = 0;
		for( DHLink link : links ) {
			if(link.hasAdjustableValue()) {
				keyframe.fkValues[j++] = link.getAdjustableValue();
			}
		}
	}

	public int getNumLinks() {
		return links.size();
	}

	public DHLink getLink(int i) {
		return links.get(i);
	}
	
	@Override
	public void getView(ViewPanel view) {
	
	}
}
