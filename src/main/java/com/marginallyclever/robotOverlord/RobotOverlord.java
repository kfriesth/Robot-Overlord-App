package com.marginallyclever.robotOverlord;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.undo.UndoManager;
import javax.vecmath.Vector3d;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLPipelineFactory;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.FPSAnimator;
import com.marginallyclever.convenience.PrimitiveSolids;
import com.marginallyclever.robotOverlord.entity.Entity;
import com.marginallyclever.robotOverlord.entity.primitives.BlenderCameraEntity;
import com.marginallyclever.robotOverlord.entity.primitives.CameraEntity;
import com.marginallyclever.robotOverlord.entity.primitives.PhysicalEntity;
import com.marginallyclever.robotOverlord.entity.world.World;
import com.marginallyclever.robotOverlord.swingInterface.CameraViewEntity;
import com.marginallyclever.robotOverlord.swingInterface.DragBallEntity;
import com.marginallyclever.robotOverlord.swingInterface.FooterBar;
import com.marginallyclever.robotOverlord.swingInterface.InputManager;
import com.marginallyclever.robotOverlord.swingInterface.SoundSystem;
import com.marginallyclever.robotOverlord.swingInterface.Splitter;
import com.marginallyclever.robotOverlord.swingInterface.ViewCubeEntity;
import com.marginallyclever.robotOverlord.swingInterface.actions.ActionEntitySelect;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandAbout;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandAboutControls;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandCheckForUpdate;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandForums;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandNew;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandOpen;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandQuit;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandRedo;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandSaveAs;
import com.marginallyclever.robotOverlord.swingInterface.commands.CommandUndo;
import com.marginallyclever.robotOverlord.swingInterface.translator.Translator;
import com.marginallyclever.robotOverlord.swingInterface.view.View;
import com.marginallyclever.robotOverlord.swingInterface.view.ViewPanel;
import com.marginallyclever.util.PropertiesFileHelper;

/**
 * Robot Overlord (RO) is the top-level controller of an application to educate robots.
 * It is built around good design patterns.
 * @see https://github.com/MarginallyClever/Robot-Overlord-App
 * 
 * @author Dan Royer
 *
 */
public class RobotOverlord extends Entity implements MouseListener, MouseMotionListener, GLEventListener, WindowListener, UndoableEditListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6061714880126301427L;
	
	public static final String APP_TITLE = "Robot Overlord";
	public static final  String APP_URL = "https://github.com/MarginallyClever/Robot-Overlord";
	
	// used for checking the application version with the github release, for "there is a new version available!" notification
	final static public String VERSION = PropertiesFileHelper.getVersionPropertyValue();
	
	
	// Scene container
	protected World world = new World();
	// The currently selected entity to edit.
	// This is equivalent to the cursor position in a text editor.
	// This is equivalent to the currently selected directory in an OS.
	protected transient Entity selectedEntity; 
	
	// To move selected items in 3D
	protected DragBallEntity dragBall = new DragBallEntity();
	// The box in the top right of the user view that shows your orientation in the world.
	// TODO probably doesn't belong here, it's per-user?  per-camera?
	protected transient ViewCubeEntity viewCube = new ViewCubeEntity();
	// Wraps all the projection matrix stuff. 
	public CameraViewEntity cameraView = new CameraViewEntity();
	// At least one camera to prevent disaster. 
	public CameraEntity camera = new BlenderCameraEntity();
	
	
	// click on screen to change which entity is selected
	// select buffer
	protected transient IntBuffer pickBuffer = null;
	// select buffer depth
	static final public int SELECT_BUFFER_SIZE=256;
	// when to pick
	protected transient boolean pickNow;
	// where on screen to pick
	protected transient double pickX, pickY;
	// ray picking visualization
	protected transient Vector3d pickForward=new Vector3d();
	protected transient Vector3d pickRight=new Vector3d();
	protected transient Vector3d pickUp=new Vector3d();
	protected transient Vector3d pickRay=new Vector3d();
	
    // The animator keeps things moving
    private FPSAnimator animator;
    // animation speed
	static final public int DEFAULT_FRAMES_PER_SECOND = 30;
    
    // timing for animations
    protected long startTime;
    protected long lastTime;
    private double frameDelay;
    private double frameLength;
	
	// settings
    protected Preferences prefs;
	protected String[] recentFiles = {"","","","","","","","","",""};
	
	// should I check the state of the OpenGL stack size?  true=every frame, false=never
	protected boolean checkStackSize = false;

	// menus
    // main menu bar
	protected transient JMenuBar mainMenu;
	
	// The main frame of the GUI
    protected JFrame mainFrame; 
	// the main view
	protected Splitter splitUpDown;
		// top part
		protected Splitter splitLeftRight;
		// bottom part
		protected Splitter rightFrameSplitter;
	// the 3D view of the scene
	protected GLJPanel glCanvas;
	// tree like view of all entities in the scene
	protected JPanel entityTree;
	// panel view of edit controls for the selected entity
	protected JPanel selectedEntityPanel;
	
	//protected SecondaryPanel secondaryPanel;
	protected FooterBar footerBar;
	
	// undo/redo system
	private UndoManager undoManager = new UndoManager();
	private CommandUndo commandUndo;
	private CommandRedo commandRedo;

	// mouse steering controls
	private boolean isMouseIn=false;
	
	
 	protected RobotOverlord() {
 		super();
		prefs = Preferences.userRoot().node("Evil Overlord");  // Secretly evil?  Nice.

		//System.out.println("\n\n*** CLASSPATH="+System.getProperty("java.class.path")+" ***\n\n");
		if(GraphicsEnvironment.isHeadless()) {
			throw new RuntimeException("RobotOverlord cannot be run headless...yet.");
		}
		
		Translator.start();

		commandUndo = new CommandUndo(undoManager);
		commandRedo = new CommandRedo(undoManager);
        commandUndo.setRedoCommand(commandRedo);
    	commandRedo.setUndoCommand(commandUndo);

 		setName("Robot Overlord");
		
 		addChild(cameraView);
 		addChild(camera);
        addChild(world);
 		addChild(dragBall);
 		addChild(viewCube);
 		
 		cameraView.attachedTo.set(camera.getCanonicalName());
        
        // ..with default setting.  TODO save & load whole world and all its Entities.
        world.createDefaultWorld();
		
		SoundSystem.start();
		InputManager.start();

        // initialize the screen picking system (to click on a robot and get its context sensitive menu)
        pickNow = false;
        pickBuffer = Buffers.newDirectIntBuffer(RobotOverlord.SELECT_BUFFER_SIZE);
        selectedEntity = null;
        
        
		// start the main application frame - the largest visible rectangle on the screen with the minimize/maximize/close buttons.
        mainFrame = new JFrame( APP_TITLE + " " + VERSION ); 
    	mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.setSize( 1224, 768 );
        mainFrame.setLayout(new java.awt.BorderLayout());

      	// this class listens to the window
        mainFrame.addWindowListener(this);
    	
        // add to the frame a menu bar
        mainMenu = new JMenuBar();
        mainFrame.setJMenuBar(mainMenu);
        // now that we have everything built, set up the menus.
        buildMainMenu();
		
        {
	        {
	        	/**
	        	 * build OpenGL 3D view
	        	 */
	        	{
		            GLCapabilities caps = new GLCapabilities(null);
		            caps.setSampleBuffers(true);
		            caps.setHardwareAccelerated(true);
		            caps.setNumSamples(4);
		            glCanvas = new GLJPanel(caps);
		            glCanvas.addGLEventListener(this);  // this class also listens to the glcanvas (messy!) 
		            glCanvas.addMouseListener(this);  // this class also listens to the mouse button clicks.
		            glCanvas.addMouseMotionListener(this);  // this class also listens to the mouse movement.
		            // not sure what good this does here...
		            Dimension minimumSize = new Dimension(300,300);
		            glCanvas.setMinimumSize(minimumSize);
	        	}
	        	{
	        		// build the initial entity tree
			        {
				        entityTree = new JPanel();
				        updateEntityTree();
			        }
			        // build the panel to display controls for the selected entity
			        {
				        selectedEntityPanel = new JPanel();
				        selectedEntityPanel.setLayout(new BorderLayout());
			        }
			        
			        // the right hand stuff			        
					rightFrameSplitter = new Splitter(JSplitPane.VERTICAL_SPLIT);
					rightFrameSplitter.add(new JScrollPane(entityTree));
					rightFrameSplitter.add(new JScrollPane(selectedEntityPanel));
					// make sure the master panel can't be squished.
		            Dimension minimumSize = new Dimension(300,300);
			        rightFrameSplitter.setMinimumSize(minimumSize);
			        rightFrameSplitter.setDividerLocation(-1);
		        }
			        
		        // split the mainframe in two vertically
		        splitLeftRight = new Splitter(JSplitPane.HORIZONTAL_SPLIT);
		        splitLeftRight.setLeftComponent(glCanvas);
		        splitLeftRight.setRightComponent(rightFrameSplitter);
	        }
	        // Also split up/down
	        splitUpDown = new Splitter(JSplitPane.VERTICAL_SPLIT);
	        splitUpDown.setTopComponent(splitLeftRight);
	        splitUpDown.setBottomComponent(footerBar = new FooterBar(mainFrame));
	        
			// add the split panel to the main frame
	        mainFrame.add(splitUpDown);
	 	}
        // make it visible
        mainFrame.setVisible(true);
        
        // setup the animation system.
        frameDelay=0;
        frameLength=1.0f/(float)DEFAULT_FRAMES_PER_SECOND;
      	animator = new FPSAnimator(DEFAULT_FRAMES_PER_SECOND*2);
        animator.add(glCanvas);

        // record the start time of the application, also the end of the core initialization process.
        lastTime = startTime = System.currentTimeMillis();
        // start the main application loop.  it will call display() repeatedly.
        animator.start();
    }
 	
	public JFrame getMainFrame() {
		return mainFrame;
	}
	
	public UndoManager getUndoManager() {
		return undoManager;
	}
	
	public World getWorld() {
		return world;
	}
	
	public Entity getPickedEntity() {
		return selectedEntity;
	}
	
	// This is a ViewTree of the root entity.
	// Only add branches of the tree, ignore all leaves.  leaves *should* be handled by the ViewPanel of a single entity.
	protected DefaultMutableTreeNode createTreeNodes(Entity e) {
		DefaultMutableTreeNode parent = new DefaultMutableTreeNode(e);
		for(Entity child : e.getChildren() ) {
			if(!child.getChildren().isEmpty()) {
				parent.add(createTreeNodes(child));
			}
		}
		return parent;
	}
	
	/**
	 * Change the right-side context menu.  contextMenu is already a JScrollPane.
	 * Get all the {@link EntityPanel}s for a {@link Entity}.  
	 * @param panel
	 * @param title
	 */
	public void updateSelectedEntityPanel(Entity e) {
        // list of all entities in system, starting with world.
		if(selectedEntityPanel==null) return;

		selectedEntityPanel.removeAll();

		if(e!=null) {
			ViewPanel vp = new ViewPanel(this);
			vp.addReadOnly(e.getCanonicalName());
			e.getView(vp);
			selectedEntityPanel.add(vp,BorderLayout.PAGE_START);
		}

		selectedEntityPanel.repaint();
		selectedEntityPanel.revalidate();
		
		//rightFrameSplitter.setDividerLocation(180);
	}

    /**
     * list all entities in the world.  Double click an item to get its panel.
     * See https://docs.oracle.com/javase/7/docs/api/javax/swing/JTree.html
     */
	public void updateEntityTree() {
		// list all objects in scene
	    DefaultMutableTreeNode top = createTreeNodes(this);
		JTree tree = new JTree(top);

	    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
	    tree.setShowsRootHandles(true);
	    tree.addMouseListener(new MouseAdapter() {
		    public void mousePressed(MouseEvent e) {
		        TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
		        if(selPath != null) {
		            if(e.getClickCount() == 1) {
		                //mySingleClick(selRow, selPath);
		            	DefaultMutableTreeNode o = (DefaultMutableTreeNode)selPath.getLastPathComponent();
		            	pickEntity((Entity)(o.getUserObject()));
		            } else if(e.getClickCount() == 2) {
		                //myDoubleClick(selRow, selPath);
		            }
		        }
		    }
		});
		//tree.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		
		if(entityTree.getComponentCount()==1) {
			JTree oldTree = (JTree)entityTree.getComponent(0);
			// preserve the original expansions
			ArrayList<TreePath> expanded = new ArrayList<TreePath>();
			for(int i=0;i<oldTree.getRowCount();++i) {
				if(oldTree.isExpanded(i)) {
					expanded.add(oldTree.getPathForRow(i));
				}
			}
			// restore the expanded paths
			for(TreePath p : expanded) {
				tree.expandPath(p);
			}
			// restore the selected paths
			TreePath[] paths = oldTree.getSelectionPaths();
			tree.setSelectionPaths(paths);
		}
		
		entityTree.removeAll();
		entityTree.setLayout(new BorderLayout());
		entityTree.add(tree,BorderLayout.CENTER);
	}

	public void saveWorldToFile(String filename) {
		saveWorldToFileJSON(filename);
		//saveWorldToFileSerializable(filename);
	}
	
	public void loadWorldFromFile(String filename) {
		loadWorldFromFileJSON(filename);
		//loadWorldFromFileSerializable(filename);
	}

	protected ObjectMapper getObjectMapper() {
		ObjectMapper om = new ObjectMapper();
		om.enable(SerializationFeature.INDENT_OUTPUT);/*
		om.setVisibility(
					om.getSerializationConfig().
					getDefaultVisibilityChecker().
					withFieldVisibility(Visibility.ANY).
					withGetterVisibility(Visibility.NONE));*/
		return om;
	}
	
	public void saveWorldToFileJSON(String filename) {
		ObjectMapper om = getObjectMapper();
		try {
			om.writeValue(new File(filename), world);
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void loadWorldFromFileJSON(String filename) {
		ObjectMapper om = getObjectMapper();
		try {
			world = (World)om.readValue(new File(filename), World.class);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * See http://www.javacoffeebreak.com/text-adventure/tutorial3/tutorial3.html
	 * @param filename
	 */
	@Deprecated
	public void saveWorldToFileSerializable(String filename) {
		FileOutputStream fout=null;
		ObjectOutputStream objectOut=null;
		try {
			fout = new FileOutputStream(filename);
			objectOut = new ObjectOutputStream(fout);
			objectOut.writeObject(world);
		} catch(java.io.NotSerializableException e) {
			System.out.println("World can't be serialized.");
			e.printStackTrace();
		} catch(IOException e) {
			System.out.println("World save failed.");
			e.printStackTrace();
		} finally {
			if(objectOut!=null) {
				try {
					objectOut.close();
				} catch(IOException e) {}
			}
			if(fout!=null) {
				try {
					fout.close();
				} catch(IOException e) {}
			}
		}
	}

	/**
	 *  See http://www.javacoffeebreak.com/text-adventure/tutorial3/tutorial3.html
	 * @param filename
	 */
	@Deprecated
	public void loadWorldFromFileSerializable(String filename) {
		FileInputStream fin=null;
		ObjectInputStream objectIn=null;
		try {
			// Create a file input stream
			fin = new FileInputStream(filename);
	
			// Create an object input stream
			objectIn = new ObjectInputStream(fin);
	
			// Read an object in from object store, and cast it to a GameWorld
			this.world = (World) objectIn.readObject();
		} catch(IOException e) {
			System.out.println("World load failed (file io).");
			e.printStackTrace();
		} catch(ClassNotFoundException e) {
			System.out.println("World load failed (class not found)");
			e.printStackTrace();
		} finally {
			if(objectIn!=null) {
				try {
					objectIn.close();
				} catch(IOException e) {}
			}
			if(fin!=null) {
				try {
					fin.close();
				} catch(IOException e) {}
			}
		}
	}

	public void newWorld() {
		this.world = new World();
		pickEntity(null);
	}
	
	/*
	
	// stuff for trying to find and load plugins, part of future expansion
	 
	private String getPath(Class cls) {
	    String cn = cls.getName();
	    //System.out.println("cn "+cn);
	    String rn = cn.replace('.', '/') + ".class";
	    //System.out.println("rn "+rn);
	    String path = getClass().getClassLoader().getResource(rn).getPath();
	    //System.out.println("path "+path);
	    int ix = path.indexOf("!");
	    if(ix >= 0) {
	        path = path.substring(0, ix);
	    }
	    return path;
	}
	
	protected void EnumerateJarContents(String absPathToJarFile) throws IOException {
	    JarFile jarFile = new JarFile(absPathToJarFile);
	    Enumeration<JarEntry> e = jarFile.entries();
	    while (e.hasMoreElements()) {
			_EnumerateJarContents(e.nextElement());
		}
	}
	
	private static void _EnumerateJarContents(Object obj) {
       JarEntry entry = (JarEntry)obj;
       String name = entry.getName();
       long size = entry.getSize();
       long compressedSize = entry.getCompressedSize();
       System.out.println(name + "\t" + size + "\t" + compressedSize);
     }
	
	// Load a class from a Jar file.
	// @param absPathToJarFile c:\some\path\myfile.jar
	// @param className like com.mypackage.myclass
	protected void LoadClasses(String absPathToJarFile,String className) {
		File file  = new File(absPathToJarFile);
		try {
			URL url = file.toURI().toURL();  
			URL[] urls = new URL[]{url};
			ClassLoader cl = new URLClassLoader(urls);
			Class cls = cl.loadClass(className);
		}
		catch(MalformedURLException e) {}
		catch(ClassNotFoundException e) {}
	}
	*/
	
	public void buildMainMenu() {
		mainMenu.removeAll();
		
		JMenu menu;
		
		menu = new JMenu(APP_TITLE);
		menu.add(new JMenuItem(new CommandNew(this)));        	
		menu.add(new JMenuItem(new CommandOpen(this)));
		menu.add(new JMenuItem(new CommandSaveAs(this)));
		menu.add(new JSeparator());
		menu.add(new JMenuItem(new CommandQuit(this)));
		mainMenu.add(menu);
		
        menu = new JMenu("Edit");
        menu.add(new JMenuItem(commandUndo));
        menu.add(new JMenuItem(commandRedo));
        mainMenu.add(menu);
    	
        menu = new JMenu("Help");
        menu.add(new JMenuItem(new CommandAboutControls()));
		menu.add(new JMenuItem(new CommandForums()));
		menu.add(new JMenuItem(new CommandCheckForUpdate()));
		menu.add(new JMenuItem(new CommandAbout()));
        mainMenu.add(menu);
    	
    	// done
        mainMenu.updateUI();
	}

	/**
	 * changes the order of the recent files list in the File submenu, saves the updated prefs, and refreshes the menus.
	 * @param filename the file to push to the top of the list.
	 */
	public void updateRecentFiles(String filename) {
		int cnt = recentFiles.length;
		String [] newFiles = new String[cnt];
		
		newFiles[0]=filename;
		
		int i,j=1;
		for(i=0;i<cnt;++i) {
			if(!filename.equals(recentFiles[i]) && recentFiles[i] != "") {
				newFiles[j++] = recentFiles[i];
				if(j == cnt ) break;
			}
		}

		recentFiles=newFiles;

		// update prefs
		for(i=0;i<cnt;++i) {
			if( recentFiles[i]==null ) recentFiles[i] = "";
			if( !recentFiles[i].isEmpty() ) {
				prefs.put("recent-files-"+i, recentFiles[i]);
			}
		}
	}
	
	// A file failed to load.  Remove it from recent files, refresh the menu bar.
	public void removeRecentFile(String filename) {
		int i;
		for(i=0;i<recentFiles.length-1;++i) {
			if(recentFiles[i]==filename) {
				break;
			}
		}
		for(;i<recentFiles.length-1;++i) {
			recentFiles[i]=recentFiles[i+1];
		}
		recentFiles[recentFiles.length-1]="";

		// update prefs
		for(i=0;i<recentFiles.length;++i) {
			if(!recentFiles[i].isEmpty()) {
				prefs.put("recent-files-"+i, recentFiles[i]);
			}
		}
	}

    @Override
    public void reshape( GLAutoDrawable drawable, int x, int y, int width, int height ) {
    	GL2 gl2 = drawable.getGL().getGL2();
    	// turn on vsync
        gl2.setSwapInterval(1);

        // set up the projection matrix
        cameraView.setCanvasWidth(glCanvas.getSurfaceWidth());
        cameraView.setCanvasHeight(glCanvas.getSurfaceHeight());
		cameraView.render(gl2);


		// set opengl options
		gl2.glDepthFunc(GL2.GL_LESS);
		gl2.glEnable(GL2.GL_DEPTH_TEST);
		gl2.glDepthMask(true);

		// make things pretty
    	gl2.glEnable(GL2.GL_LINE_SMOOTH);      
        gl2.glEnable(GL2.GL_POLYGON_SMOOTH);
        gl2.glHint(GL2.GL_POLYGON_SMOOTH_HINT, GL2.GL_NICEST);
        
        // Scale normals using the scale of the transform matrix so that lighting is sane.
        // This is more efficient than gl2.gleEnable(GL2.GL_NORMALIZE);
		//gl2.glEnable(GL2.GL_RESCALE_NORMAL);
		gl2.glEnable(GL2.GL_NORMALIZE);
        
        gl2.glEnable(GL2.GL_BLEND);
        gl2.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

        // TODO add a settings toggle for this option, it really slows down older machines.
        gl2.glEnable(GL2.GL_MULTISAMPLE);
        
        int buf[] = new int[1];
        int sbuf[] = new int[1];
        gl2.glGetIntegerv(GL2.GL_SAMPLES, buf, 0);
        gl2.glGetIntegerv(GL2.GL_SAMPLE_BUFFERS, sbuf, 0);
    }
    
    @Override
    public void init( GLAutoDrawable drawable ) {
    	// Use debug pipeline
    	boolean glDebug=false;
    	boolean glTrace=false;
    	
        GL gl = drawable.getGL();
        
        if(glDebug) {
            try {
                // Debug ..
                gl = gl.getContext().setGL( GLPipelineFactory.create("com.jogamp.opengl.Debug", null, gl, null) );
            } catch (Exception e) {
            	e.printStackTrace();
            }
        }

        if(glTrace) {
            try {
                // Trace ..
                gl = gl.getContext().setGL( GLPipelineFactory.create("com.jogamp.opengl.Trace", null, gl, new Object[] { System.err } ) );
            } catch (Exception e) {
            	e.printStackTrace();
            }
        }
    }

    @Override
    public void dispose( GLAutoDrawable drawable ) {}
    
    /**
     * Draw the 3D scene.  Called ~30/s. Also does other update tasks and polls input.
     */
    @Override
    public void display( GLAutoDrawable drawable ) {
        long nowTime = System.currentTimeMillis();
        double dt = (nowTime - lastTime)*0.001;  // to seconds
    	lastTime = nowTime;
    	//System.out.println(dt);
    	
    	// UPDATE STEP
    	
    	frameDelay+=dt;
    	if(frameDelay>frameLength) {
   			frameDelay-=frameLength;

	    	InputManager.update(isMouseIn);

	    	this.update( frameLength );
    	}

    	// RENDER STEP

    	GL2 gl2 = drawable.getGL().getGL2();

		if(checkStackSize) {
    		IntBuffer stackDepth = IntBuffer.allocate(1);
    		gl2.glGetIntegerv (GL2.GL_MODELVIEW_STACK_DEPTH,stackDepth);
    		System.out.print("stack depth start = "+stackDepth.get(0));
		}	

		gl2.glClearColor(0.85f,0.85f,0.85f,1.0f);
        gl2.glClear(GL2.GL_DEPTH_BUFFER_BIT | GL2.GL_COLOR_BUFFER_BIT);

        gl2.glMatrixMode(GL2.GL_MODELVIEW);
		gl2.glLoadIdentity();
		
		// 
        cameraView.render(gl2);

        world.render(gl2);

        showPickingTest(gl2);
        
        // overlays
		dragBall.render(gl2);
		viewCube.render(gl2);
				
        if(pickNow) {
	        pickNow=false;
	        gl2.glClear(GL2.GL_DEPTH_BUFFER_BIT);
	        int pickName = findItemUnderCursor(gl2);
        	//System.out.println(System.currentTimeMillis()+" pickName="+pickName);
        	pickIntoWorld(pickName);
        }
		
		if(checkStackSize) {
    		IntBuffer stackDepth = IntBuffer.allocate(1);
			gl2.glGetIntegerv (GL2.GL_MODELVIEW_STACK_DEPTH,stackDepth);
			System.out.println("stack depth end = "+stackDepth.get(0));
		}
    }

	protected void showPickingTest(GL2 gl2) {
		if(pickForward.lengthSquared()<1e-6) return;
		
		gl2.glPushMatrix();
		gl2.glDisable(GL2.GL_LIGHTING);

		Vector3d forward = new Vector3d();
		forward.set(pickForward);
		forward.scale(10);
		forward.sub(camera.getPosition());
		gl2.glColor3f(1,0,0);
		PrimitiveSolids.drawStar(gl2, forward);
		
		forward.set(pickForward);
		forward.scale(10);
		forward.add(pickRight);
		forward.sub(camera.getPosition());
		gl2.glColor3f(0,1,0);
		PrimitiveSolids.drawStar(gl2, forward);
		
		forward.set(pickForward);
		forward.scale(10);
		forward.add(pickUp);
		forward.sub(camera.getPosition());
		gl2.glColor3f(0,0,1);
		PrimitiveSolids.drawStar(gl2, forward);
		
		forward.set(pickRay);
		forward.scale(10);
		forward.sub(camera.getPosition());
		gl2.glColor3f(1,1,0);
		PrimitiveSolids.drawStar(gl2, forward);
		
		gl2.glEnable(GL2.GL_LIGHTING);
		gl2.glPopMatrix();
	}
	
    /**
     * Use glRenderMode(GL_SELECT) to ray pick the item under the cursor.
     * https://github.com/sgothel/jogl-demos/blob/master/src/demos/misc/Picking.java
     * http://web.engr.oregonstate.edu/~mjb/cs553/Handouts/Picking/picking.pdf
     * @param gl2 the openGL render context
     */
    protected int findItemUnderCursor(GL2 gl2) {
    	// set up the buffer that will hold the names found under the cursor in furthest to closest.
        gl2.glSelectBuffer(SELECT_BUFFER_SIZE, pickBuffer);
        // change the render mode
		gl2.glRenderMode( GL2.GL_SELECT );
		// wipe the select buffer
		gl2.glInitNames();
		
		cameraView.renderPick(gl2,pickX,pickY);

        // render in selection mode, without advancing time in the simulation.
        world.render(gl2);

        //gl2.glPushName(0);

        // get the picking results and return the render mode to the default 
        int hits = gl2.glRenderMode( GL2.GL_RENDER );

        //gl2.glPopName();

		//System.out.println("\n"+hits+" PICKS @ "+pickX+","+pickY);
        float z1;
		//float z2;
		
        float zMinBest = Float.MAX_VALUE;
    	int i, j, index=0, nameCount, pickName, bestPickName=0;
    	
    	for(i=0;i<hits;++i) {
    		nameCount=pickBuffer.get(index++);
    		z1 = (float) (pickBuffer.get(index++) & 0xffffffffL) / (float)0x7fffffff;
    		
    		//z2 = (float) (selectBuffer.get(index++) & 0xffffffffL) / (float)0x7fffffff;
    		index++;
    		
    		//System.out.print("  names="+nameCount+" zMin="+z1);//+" zMax="+z2);
    		//String add=": ";
			for(j=0;j<nameCount-1;++j) {
    			pickName = pickBuffer.get(index++);
        		//System.out.print(add+pickName);
        		//add=", ";
			}
			if(nameCount>0) {
				pickName = pickBuffer.get(index++);
        		//System.out.print(add+pickName);
        		if(zMinBest > z1) {
        			zMinBest = z1;
        			bestPickName = pickName;
        		}
    		}
    		//System.out.println();
    	}
    	return bestPickName;
    }
    
    public void pickIntoWorld(int pickName) {
    	Entity next = world.pickPhysicalEntityWithName(pickName);
		
		undoableEditHappened(new UndoableEditEvent(this,new ActionEntitySelect(this,selectedEntity,next) ) );
    }
	
	public void pickEntity(Entity e) {
		if(e==null) return;
		
		System.out.println("Picked "+e.getCanonicalName());
		
		selectedEntity=e;

		if(e instanceof PhysicalEntity && e != dragBall) {
			dragBall.setSubject((PhysicalEntity)e);
		}
		//updateEntityTree();
		updateSelectedEntityPanel(e);
	}
    
	public void pickCamera() {
		PhysicalEntity camera = cameraView.getAttachedTo();
		if(camera!=null) {
			pickEntity(camera);
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		// if they dragged the cursor around before unclicking, don't pick.
		if (e.getClickCount() == 2) {
			pickX=e.getX();
			pickY=e.getY();
			pickNow=true;
		}
	}
	@Override
	public void mousePressed(MouseEvent e) {
		pickX=e.getX();
		pickY=e.getY();		
		cameraView.pressed();
	}
	@Override
	public void mouseReleased(MouseEvent e) {
		cameraView.released();
	}
	@Override
	public void mouseEntered(MouseEvent e) {
		isMouseIn=true;
		glCanvas.requestFocus();
	}
	@Override
	public void mouseExited(MouseEvent e) {
		isMouseIn=false;
	}
	@Override
	public void mouseDragged(MouseEvent e) {
        cameraView.setCursor(e.getX(),e.getY());
	}
	@Override
	public void mouseMoved(MouseEvent e) {
        cameraView.setCursor(e.getX(),e.getY());
	}


	@Override
	public void windowActivated(WindowEvent arg0) {}
	@Override
	public void windowClosed(WindowEvent arg0) {}
	@Override
	public void windowDeactivated(WindowEvent arg0) {}
	@Override
	public void windowDeiconified(WindowEvent arg0) {}
	@Override
	public void windowIconified(WindowEvent arg0) {}
	@Override
	public void windowOpened(WindowEvent arg0) {}
	@Override
	public void windowClosing(WindowEvent arg0) {
		confirmClose();
	}
	
	
	public void confirmClose() {
        int result = JOptionPane.showConfirmDialog(
                mainFrame,
                "Are you sure you want to quit?",
                "Quit",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
        	mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        	// Run this on another thread than the AWT event queue to
	        // make sure the call to Animator.stop() completes before
	        // exiting
	        new Thread(new Runnable() {
	            public void run() {
	              animator.stop();
	              mainFrame.dispose();
	            }
	        }).start();
        }
	}

	public static void main(String[] argv) {
	    //Schedule a job for the event-dispatching thread:
	    //creating and showing this application's GUI.
	    javax.swing.SwingUtilities.invokeLater(new Runnable() {
	        public void run() {
	        	new RobotOverlord();
	        }
	    });
	}

	@Override
	public void undoableEditHappened(UndoableEditEvent e) {
		undoManager.addEdit(e.getEdit());
		commandUndo.updateUndoState();
		commandRedo.updateRedoState();
	}
	
	/**
	 * Deep search for a child with this name.
	 * @param name
	 * @return the entity.  null if nothing found.
	 */
	public Entity findChildWithName(String name) {
		ArrayList<Entity> list = new ArrayList<Entity>();
		list.add(world);
		while( !list.isEmpty() ) {
			Entity obj = list.remove(0);
			String objectName = obj.getName();
			if(name.equals(objectName)) return obj;
			list.addAll(obj.getChildren());
		}
		
		return null;
	}
	
	@Override
	public void getView(View view) {
		
	}
}
