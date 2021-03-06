package grafl.sy.renderer;

import static android.opengl.GLES20.*;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.Matrix;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import grafl.sy.logic.Logic;
//import grafl.util.SimpleProfiler;



public class LevelRenderer extends Renderer
{
	// static SimpleProfiler profiler_draw = new SimpleProfiler("LevelRenderer.draw", 300);

	public final static int FRAMESPERSTEP = 150;
	
	final static int TILEWIDTH = 60;
	final static int TILEHEIGHT = 60;
	final static int ATLASWIDTH = 2048;
	final static int ATLASHEIGHT = 2048; // 1024; 
	final static int TILESPERROW = ATLASWIDTH/(TILEWIDTH+2);
	final static int TILEROWS    = ATLASHEIGHT/(TILEHEIGHT+2);
	final static int ATLASTILES = TILESPERROW*TILEROWS;			
			
	final static int MAXTILES = 64*64;
	
    private final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;                  "+
            "uniform int uScreenTileSize;              "+    // pixel size of tile on screen
            "attribute vec2 aCorner;                   "+    // one of 0,0  0,1  1,0,  1,1
            "attribute vec4 aTile;                     "+    // input as x,y,tile,modifiers
            "varying vec2 vTextureCoordinates;         "+    // output to fragment shader
            "float idiv(float a, float b) {            "+    //  to work around bugs in integer arithmetic
            "  return floor(a/b+0.00001);              "+    //  use only floats for calculations even if
            "}                                         "+    //  we really meant to do integer division
            "void main() {                             "+
            "  float ty = idiv(aTile[2],"+TILESPERROW+".0);   "+     // y position (in tiles in atlas)
            "  float tx = aTile[2]-ty*"+TILESPERROW+".0;      "+     // x position (in tiles in atlas)
			"  float rotate = idiv(aTile[3],60.0);            "+     // rotation modifier
            "  float shrink = aTile[3]-rotate*60.0;           "+     // shrink modifier
            "  vTextureCoordinates[0] = (tx*("+(TILEWIDTH+2)+".0)+1.0+aCorner[0]*"+TILEWIDTH+".0)/"+ATLASWIDTH+".0;  "+
            "  vTextureCoordinates[1] = (ty*("+(TILEWIDTH+2)+".0)+1.0+aCorner[1]*"+TILEWIDTH+".0)/"+ATLASHEIGHT+".0; "+
			"  float px = aCorner[0]-0.5;                 "+    // bring center of tile to 0/0 
			"  float py = aCorner[1]-0.5;                 "+
            "  px = px*(1.0-shrink/60.0);                 "+    // apply shrink value
            "  py = py*(1.0-shrink/60.0);                 "+
            "  float si = sin(rotate*0.017453292519943);  "+   // degrees -> rad 
            "  float co = cos(rotate*0.017453292519943);  "+
            "  float px2 = px*co + py*si;                 "+    // rotation value
            "  py = (-px*si) + py*co;                     "+
            "  px = px2;                                  "+
            "  vec4 p;                                    "+
            "  p[0] = aTile[0] + (px+0.5)*float(uScreenTileSize); "+
            "  p[1] = aTile[1] + (py+0.5)*float(uScreenTileSize); "+
            "  p[2] = 0.0;                               "+
            "  p[3] = 1.0;                               "+
            "  gl_Position = uMVPMatrix * p;             "+
            "} ";
    
    private final String fragmentShaderCode =
    		"varying mediump vec2 vTextureCoordinates;        "+  // input from vertex shader
            "uniform sampler2D uTexture;                      "+  // uniform specifying the texture 
            "void main() {                                    "+
            "   gl_FragColor = texture2D(uTexture,vTextureCoordinates);  "+  
            "}                                                "+
            "";
    
    private final int program;
    private final int uMVPMatrix;
    private final int uScreenTileSize;
    private final int uTexture;
    private final int aCorner;
    private final int aTile;
    
    private final int iboIndex;       // buffer holding short
    private final int vboCorner;      // buffer holding byte[4][2] = {0,0},{1,0},{0,1},{1,1}  for each tile  
    private final int vboTile;        // buffer holding short[4] - x,y,tile,modifier  for each tile corner,  dublicated 4 times 
    private final int txTexture;      // texture buffer
    
    // client-side buffers to prepare the data before moving it into their gl counterparts
	private final ShortBuffer bufferTile;   // holding x,y,tile		
    private final float[] matrix;       // projection matrix
    private final float[] matrix2;      // projection matrix for second player
    private boolean havematrix2;        // is second matrix present?
	private int screentilesize;         // size of tiles on screen in pixels

    private final boolean tmp_disable_static_tile[];
	
	// translation of piece code to tile indizes
	private final int[][] piece2tile;	

	// special tiles for context specific appearances
	private final int[][] earthtiles;
	private final int[][] walltiles;
	private final int[][] roundwalltiles;
	private final int[][] acidtiles_leftedge;
	private final int[][] acidtiles_rightedge;
	private final int[][] acidtiles_bothedges;
	private final int[][] acidtiles_noedge;
	
	// animation information
	private int[] anim_man1_left;
	private int[] anim_man1_right;
	private int[] anim_man1_up;
	private int[] anim_man1_down;
	private int[] anim_man1_digleft;
	private int[] anim_man1_digright;
	private int[] anim_man1_digup;
	private int[] anim_man1_digdown;
	private int[] anim_man1_pushleft;
	private int[] anim_man1_pushright;
	private int[] anim_man1_pushup;
	private int[] anim_man1_pushdown;
	private int[] anim_man1_blink;
	private int[] anim_man2_left;
	private int[] anim_man2_right;
	private int[] anim_man2_up;
	private int[] anim_man2_down;
	private int[] anim_man2_digleft;
	private int[] anim_man2_digright;
	private int[] anim_man2_digup;
	private int[] anim_man2_digdown;
	private int[] anim_man2_pushleft;
	private int[] anim_man2_pushright;
	private int[] anim_man2_pushup;
	private int[] anim_man2_pushdown;
	private int[] anim_man2_blink;
	
	private int[][] anim_earth_up;
	private int[][] anim_earth_down;
	private int[][] anim_earth_left;
	private int[][] anim_earth_right;
	
	private int[] anim_sapphire_fall; 
	private int[] anim_sapphire_away; 
	private int[] anim_sapphire_shine; 
	private int[] anim_sapphire_break; 
	private int[] anim_emerald_fall;
	private int[] anim_emerald_away;
	private int[] anim_emerald_shine;
	private int[] anim_citrine_fall;
	private int[] anim_citrine_away;
	private int[] anim_citrine_shine;
	private int[] anim_citrine_break;
	private int[] anim_ruby_fall;
	private int[] anim_ruby_away;	
	private int[] anim_ruby_shine;	
	private int[] anim_rock_left;
	private int[] anim_rock_right;
	private int[] anim_rockemerald_left;
	private int[] anim_rockemerald_right;
	private int[] anim_bag_left;
	private int[] anim_bag_right;
	private int[] anim_bag_opening;
	private int[] anim_bomb_fall;
	private int[] anim_bomb_left;
	private int[] anim_bomb_right;
	private int[] anim_sand;
	private int[] anim_explode0_air;
	private int[] anim_explode1_air;
	private int[] anim_explode2_air;
	private int[] anim_explode3_air;
	private int[] anim_explode4_air;
	private int[] anim_explode3_emerald;
	private int[] anim_explode4_emerald;
	private int[] anim_explode3_sapphire;
	private int[] anim_explode4_sapphire;
	private int[] anim_explode3_ruby;
	private int[] anim_explode4_ruby;
	private int[] anim_explode3_bag;
	private int[] anim_explode4_bag;
	private int[] anim_explode0_tnt;
	private int[] anim_explode1_tnt;
	private int[] anim_explode2_tnt;
	private int[] anim_explode3_tnt;
	private int[] anim_explode4_tnt;
	private int[] anim_door_red;
	private int[] anim_door_green;
	private int[] anim_door_blue;
	private int[] anim_door_yellow;
	private int[] anim_door_onetime;
	private int[] anim_door_opening;
	private int[] anim_door_closing;
	private int[] anim_cushion;
	private int[] anim_gunfire;
	private int[] anim_swamp;
	private int[] anim_swamp_left;
	private int[] anim_swamp_right;
	private int[] anim_swamp_up;
	private int[] anim_swamp_down;
	private int[] anim_drop_left;
	private int[] anim_drop_right;
	private int[] anim_createdrop;
	private int[] anim_drop;
	private int[] anim_drophit;
	private int[] anim_lorry_left;
	private int[] anim_lorry_right;
	private int[] anim_lorry_up;
	private int[] anim_lorry_down;	
	private int[] anim_lorry_left_up;
	private int[] anim_lorry_left_down;
	private int[] anim_lorry_up_right;
	private int[] anim_lorry_up_left;
	private int[] anim_lorry_right_down;
	private int[] anim_lorry_right_up;
	private int[] anim_lorry_down_left;
	private int[] anim_lorry_down_right;
	private int[] anim_bug_left;
	private int[] anim_bug_right;
	private int[] anim_bug_up;
	private int[] anim_bug_down;	
	private int[] anim_bug_left_up;
	private int[] anim_bug_left_down;
	private int[] anim_bug_up_right;
	private int[] anim_bug_up_left;
	private int[] anim_bug_right_down;
	private int[] anim_bug_right_up;
	private int[] anim_bug_down_left;
	private int[] anim_bug_down_right;
	private int[] anim_yamyam;
	private int[] anim_timebomb_away;
	private int[] anim_timebomb_placement;
	private int[] anim_timebomb10_away;
	private int[] anim_keyred_away;
	private int[] anim_keyblue_away;
	private int[] anim_keygreen_away;
	private int[] anim_keyyellow_away;
	private int[] anim_elevator;
	private int[] anim_elevatorleft;
	private int[] anim_elevatorright;
	private int[] anim_elevatorleft_throw;
	private int[] anim_elevatorright_throw;
	
	
	private int[] anim_laser_h;
	private int[] anim_laser_v;
	private int[] anim_laser_bl;
	private int[] anim_laser_br;
	private int[] anim_laser_tl;
	private int[] anim_laser_tr;
	private int[] anim_laser_left;	
	private int[] anim_laser_right;	
	private int[] anim_laser_up;	
	private int[] anim_laser_down;	
		
	
	// set up opengl  and load textures
	public LevelRenderer(Context context)
	{
		super();

		// allocate memory for projection matrix
        matrix = new float[16];
        matrix2 = new float[16];
        
        // temporary data
        tmp_disable_static_tile = new boolean[Logic.MAPWIDTH*Logic.MAPHEIGHT];
        
        // prepare special animation redirects 
        piece2tile = new int[256][];
        earthtiles = new int[16][];
        walltiles = new int[9][];
        roundwalltiles = new int[4][];
		acidtiles_leftedge = new int[2][];
		acidtiles_rightedge = new int[2][];
		acidtiles_bothedges = new int[2][];
		acidtiles_noedge = new int[2][];
        
        // create shaders and link together
        program = createProgram(vertexShaderCode,fragmentShaderCode);        
        // extract the bindings for the uniforms and attributes
        uMVPMatrix = glGetUniformLocation(program, "uMVPMatrix");
        uScreenTileSize = glGetUniformLocation(program, "uScreenTileSize");
        uTexture = glGetUniformLocation(program, "uTexture");
		aCorner = glGetAttribLocation(program, "aCorner");
		aTile = glGetAttribLocation(program, "aTile");

		// index buffer (to paint quads, picking the vertices from the correct position)
	    iboIndex = genBuffer();
    	ShortBuffer sb = ShortBuffer.allocate(MAXTILES*6);
    	for (int i=0; i<MAXTILES; i++)
    	{	sb.put((short) (0*MAXTILES+i)); 
    		sb.put((short) (1*MAXTILES+i)); 
    		sb.put((short) (2*MAXTILES+i)); 
    		sb.put((short) (1*MAXTILES+i)); 
    		sb.put((short) (3*MAXTILES+i)); 
    		sb.put((short) (2*MAXTILES+i)); 
    	}
    	sb.flip();
    	glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboIndex);
    	glBufferData(GL_ELEMENT_ARRAY_BUFFER, 2*sb.limit(), sb, GL_STATIC_DRAW);
		sb = null;

		// buffer for the tile corner identifiers 
	    vboCorner = genBuffer();
    	ByteBuffer bb = ByteBuffer.allocate(MAXTILES*4*2);
    	for (int i=0; i<MAXTILES; i++)
    	{	bb.put((byte) 0);  bb.put((byte) 0); 
    	}
    	for (int i=0; i<MAXTILES; i++)
    	{	bb.put((byte) 1);  bb.put((byte) 0); 
    	}
    	for (int i=0; i<MAXTILES; i++)
    	{	bb.put((byte) 0);  bb.put((byte) 1); 
    	}
    	for (int i=0; i<MAXTILES; i++)
    	{	bb.put((byte) 1);  bb.put((byte) 1); 
    	}
    	bb.flip();
    	glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboCorner);
    	glBufferData(GL_ELEMENT_ARRAY_BUFFER, bb.limit(), bb, GL_STATIC_DRAW);		
		bb = null;
		
    	// buffer for tiles info can not be pre-computed, but client-side and gl buffers are allocated
    	vboTile = genBuffer();
    	bufferTile = ShortBuffer.allocate(MAXTILES*4);
    	glBindBuffer(GL_ARRAY_BUFFER, vboTile);
    	glBufferData(GL_ARRAY_BUFFER, 2*(MAXTILES*4*4), null, GL_DYNAMIC_DRAW);
		
    	// create the buffer for the texture atlas
       	txTexture =genTexture();
       	glBindTexture(GL_TEXTURE_2D, txTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, ATLASWIDTH,ATLASHEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, null);
        if (glGetError()!=0)
        {	setError("Can not allocate texture for tiles");
        	return;
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR); // NEAREST);       	
    	    	
    	// load all tile bitmaps into the texture atlas                
    	loadTiles(context);
    	
    	// check for additional errors
    	if (glGetError()!=0)
    	{	setError("Error at creating LevelRenderer");
    	}    	
	}
	
	// -------------- draw the whole scene as defined by the logic -----------
	public void draw(int displaywidth, int displayheight, int screentilesize, Logic logic, int frames_until_endposition, int offx0, int offy0, int offx1, int offy1)
	{    	    
//profiler_draw.start();	
		// start up the rendering		    	
		startDrawing(displaywidth, displayheight, screentilesize, offx0,offy0, offx1,offy1);

		// determine which part of the logic area needs to be painted
		int populatedwidth = logic.getPopulatedWidth();
		int populatedheight = logic.getPopulatedHeight();				

//profiler_draw.done(0);	

    	// do first parse of dynamic info to determine which static tiles should be suppressed
		Arrays.fill(tmp_disable_static_tile,false);
		if (frames_until_endposition>0)
		{	int num = logic.getAnimationBufferSize();
    		for (int idx=0; idx<num; idx++)
    		{	int trn = logic.getAnimation(idx);
    			int x = (trn>>22) & 0x03f;
    			int y = (trn>>16) & 0x03f;
    			switch (trn & Logic.TRN_MASK)
    			{	case Logic.TRN_TRANSFORM:
    				{	tmp_disable_static_tile[x+y*Logic.MAPWIDTH] = true;
    					break;
    				}
    				case Logic.TRN_MOVEDOWN:
    				{	tmp_disable_static_tile[x+(y+1)*Logic.MAPWIDTH] = true;
    					break;
    				}
    				case Logic.TRN_MOVEUP:
    				{	tmp_disable_static_tile[x+(y-1)*Logic.MAPWIDTH] = true;
    					break;
    				}
    				case Logic.TRN_MOVELEFT:
    				{	tmp_disable_static_tile[x+y*Logic.MAPWIDTH-1] = true;
    					break;
    				}
    				case Logic.TRN_MOVERIGHT:
    				{	tmp_disable_static_tile[x+y*Logic.MAPWIDTH+1] = true;
    					break;
    				}
    				case Logic.TRN_MOVEDOWN2:
    				{	tmp_disable_static_tile[x+(y+1)*Logic.MAPWIDTH] = true;
    					tmp_disable_static_tile[x+(y+2)*Logic.MAPWIDTH] = true;
    					break;
    				}
    				case Logic.TRN_MOVEUP2:
    				{	tmp_disable_static_tile[x+(y-1)*Logic.MAPWIDTH] = true;
    					tmp_disable_static_tile[x+(y-2)*Logic.MAPWIDTH] = true;
    					break;
    				}
    				case Logic.TRN_MOVELEFT2:
    				{	tmp_disable_static_tile[x+y*Logic.MAPWIDTH-1] = true;
    					tmp_disable_static_tile[x+y*Logic.MAPWIDTH-2] = true;
    					break;
    				}
    				case Logic.TRN_MOVERIGHT2:
    				{	tmp_disable_static_tile[x+y*Logic.MAPWIDTH+1] = true;
    					tmp_disable_static_tile[x+y*Logic.MAPWIDTH+2] = true;
    					break;
    				}  
    				case Logic.TRN_HIGHLIGHT:
    				{
    					int p = trn & 0xff;
    					if ((logic.piece(x,y) & 0xff) == p)		// highlights disable static rest image of same piece
    					{
    						tmp_disable_static_tile[x+y*Logic.MAPWIDTH] = true;
    					}
    					break;
    				}		
    			}    			
    		}    				
		}		
		
		// collect the non-suppressed static tiles 
		for (int y=0; y<populatedheight; y++)
		{	for (int x=0; x<populatedwidth; x++)
			{	if (!tmp_disable_static_tile[x+y*Logic.MAPWIDTH])
				{	int[] anim = determineTileAt(logic,x,y); 
					if (anim!=null)
					{	addRestingAnimationToBuffers(screentilesize,frames_until_endposition, anim, x,y);
					}
				}
			}
		}		
		
    	// do second parse of dynamic info to create animation tiles 
    	if (frames_until_endposition>0)
    	{	int num = logic.getAnimationBufferSize();
    		for (int idx=0; idx<num; idx++)
    		{	int trn = logic.getAnimation(idx);
    			int x = (trn>>22) & 0x03f;
    			int y = (trn>>16) & 0x03f;
    			byte oldpiece = (byte)((trn>>8)&0xff);
    			byte newpiece = (byte)(trn & 0xff);
    			switch (trn & Logic.TRN_MASK)
    			{	case Logic.TRN_TRANSFORM:
    				{	int[] anim = determineTransformAnimation(oldpiece, newpiece, x,y, logic);
    					if (anim!=null)
    					{	addRestingAnimationToBuffers(screentilesize,frames_until_endposition, anim, x,y);
    					}
    					break;
    				}	
    				case Logic.TRN_MOVEDOWN:
    				{	addMoveAnimationToBuffers(screentilesize,frames_until_endposition, oldpiece,newpiece, x,y, 0,1, logic);
    					break;
    				}
    				case Logic.TRN_MOVEUP:
    				{	addMoveAnimationToBuffers(screentilesize,frames_until_endposition, oldpiece,newpiece, x,y, 0,-1, logic);
    					break;
    				}
    				case Logic.TRN_MOVELEFT:
    				{	addMoveAnimationToBuffers(screentilesize,frames_until_endposition, oldpiece,newpiece, x,y, -1,0, logic);
    					break;
    				}
    				case Logic.TRN_MOVERIGHT:
    				{	addMoveAnimationToBuffers(screentilesize,frames_until_endposition, oldpiece,newpiece, x,y, 1,0, logic);
    					break;
    				}
    				case Logic.TRN_MOVEDOWN2:
    				{	addMoveAnimationToBuffers(screentilesize,frames_until_endposition, oldpiece,newpiece, x,y, 0,2, logic);
    					break;
    				}
    				case Logic.TRN_MOVEUP2:
    				{	addMoveAnimationToBuffers(screentilesize,frames_until_endposition, oldpiece,newpiece, x,y, 0,-2, logic);
    					break;
    				}
    				case Logic.TRN_MOVELEFT2:
    				{	addMoveAnimationToBuffers(screentilesize,frames_until_endposition, oldpiece,newpiece, x,y, -2,0, logic);
    					break;
    				}
    				case Logic.TRN_MOVERIGHT2:
    				{	addMoveAnimationToBuffers(screentilesize,frames_until_endposition, oldpiece,newpiece, x,y, 2,0, logic);
    					break;
    				}    				
    				case Logic.TRN_HIGHLIGHT:
    				{	int[] anim = determineHighlightAnimation(newpiece, x,y,logic);
    					if (anim!=null)
   						{	addRestingAnimationToBuffers(screentilesize,frames_until_endposition, anim, x,y);
   						}			
    					break;
    				}    				
    			}    			
    		}    		
    	}
//profiler_draw.done(1);	
    	
//profiler_draw.done(2);	
		        		
		// send accumulated data to the screen
		flush();    	
//profiler_draw.done(4);
//profiler_draw.stop();	
	}	
			
	private void addMoveAnimationToBuffers(int screentilesize, int frames_until_endposition, byte oldpiece, byte newpiece, int x1, int y1, int dx, int dy, Logic logic)
	{
		int[] anim = determineMoveAnimation(oldpiece,newpiece,x1,y1,dx,dy,logic);
		if (anim!=null)
		{	// determine correct position
			int x2 = x1 + dx;
			int y2 = y1 + dy;
			int d = screentilesize*(FRAMESPERSTEP-frames_until_endposition)/FRAMESPERSTEP;
			int px = screentilesize*x1+d*(x2-x1);
			int py = screentilesize*y1+d*(y2-y1);
				
			// when wanting to use an animation, pick the correct tile
			if (anim.length<=FRAMESPERSTEP)
			{	addTileToBuffer(px,py,anim[frames_until_endposition]);
			}
			// draw two images superimposed over each other
			else
			{	addTileToBuffer(px,py,anim[frames_until_endposition]);
				addTileToBuffer(px,py,anim[FRAMESPERSTEP+frames_until_endposition]);
			}
		}	
	}
	
	private void addRestingAnimationToBuffers(int screentilesize, int frames_until_endposition, int[] anim, int x1, int y1)
	{
	
		// when wanting to use an animation, pick the correct tile
		if (anim.length<=FRAMESPERSTEP)
		{	addTileToBuffer(screentilesize*x1,screentilesize*y1,anim[frames_until_endposition]);
		}
		// draw two images superimposed over each other
		else
		{	addTileToBuffer(screentilesize*x1,screentilesize*y1,anim[frames_until_endposition]);
			addTileToBuffer(screentilesize*x1,screentilesize*y1,anim[FRAMESPERSTEP+frames_until_endposition]);
		}
		
	}
	
	
		
	private int[] determineMoveAnimation(byte oldpiece, byte newpiece, int x, int y, int dx, int dy, Logic logic)
	{
		switch (oldpiece)
		{	
			case Logic.ROCK:
			case Logic.ROCK_FALLING:
				if (dx<0)
				{	return anim_rock_left;					
				}
				else if (dx>0)
				{	return anim_rock_right;					
				}
				break;
			case Logic.ROCKEMERALD:
			case Logic.ROCKEMERALD_FALLING:
				if (dx<0)
				{	return anim_rockemerald_left;					
				}
				else if (dx>0)
				{	return anim_rockemerald_right;					
				}				
				break;
			case Logic.BAG:
			case Logic.BAG_FALLING:
			case Logic.BAG_OPENING:
				if (dx<0)
				{	return anim_bag_left;					
				}
				else if (dx>0)
				{	return anim_bag_right;					
				}							
				break;
			case Logic.EMERALD:
			case Logic.EMERALD_FALLING:
				if (dy>=0)
				{	return anim_emerald_fall;
				}
				break;				
			case Logic.SAPPHIRE:
			case Logic.SAPPHIRE_FALLING:
				if (dy>=0)
				{	return anim_sapphire_fall;
				}
				break;			
			case Logic.CITRINE:
			case Logic.CITRINE_FALLING:
				if (dy>=0) 
				{	return anim_citrine_fall;								
				}
				break;
			case Logic.RUBY:
			case Logic.RUBY_FALLING:
				if (dy>=0) 
				{	return anim_ruby_fall;
				}
				break;				
			case Logic.BOMB:
			case Logic.BOMB_FALLING:
				if (dx<0)
				{	return anim_bomb_left;				
				}
				else if (dx>0)
				{	return anim_bomb_right;				
				}
				else if (dy>=0)
				{	return anim_bomb_fall;
				}		
				break;		
			case Logic.DROP:
				return anim_drop;	
				
			case Logic.ELEVATOR:
				if (dy<0)
				{	return anim_elevator;
				}
				break;
			case Logic.ELEVATOR_TOLEFT:
				if (dy<0)
				{	return anim_elevatorleft;
				}
				break;
			case Logic.ELEVATOR_TORIGHT:
				if (dy<0)
				{	return anim_elevatorright;
				}
				break;
											
		}

		switch (newpiece)
		{	
			case Logic.MAN1_LEFT:  
				return anim_man1_left;
			case Logic.MAN1_RIGHT:  
				return anim_man1_right;		
			case Logic.MAN1_UP:  
				return anim_man1_up;		
			case Logic.MAN1_DOWN:  
				return anim_man1_down;		
			case Logic.MAN1_DIGLEFT:  
				return anim_man1_digleft;		
			case Logic.MAN1_DIGRIGHT:  
				return anim_man1_digright;		
			case Logic.MAN1_DIGUP:  
				return anim_man1_digup;		
			case Logic.MAN1_DIGDOWN:  
				return anim_man1_digdown;		
			case Logic.MAN1_PUSHLEFT:  
				return anim_man1_pushleft;		
			case Logic.MAN1_PUSHRIGHT:  
				return anim_man1_pushright;		
			case Logic.MAN1_PUSHUP:  
				return anim_man1_pushup;		
			case Logic.MAN1_PUSHDOWN:  
				return anim_man1_pushdown;		
			case Logic.MAN2_LEFT:  
				return anim_man2_left;
			case Logic.MAN2_RIGHT: 
				return anim_man2_right;		
			case Logic.MAN2_UP:  
				return anim_man2_up;		
			case Logic.MAN2_DOWN:  
				return anim_man2_down;		
			case Logic.MAN2_DIGLEFT:  
				return anim_man2_digleft;		
			case Logic.MAN2_DIGRIGHT:  
				return anim_man2_digright;		
			case Logic.MAN2_DIGUP:  
				return anim_man2_digup;		
			case Logic.MAN2_DIGDOWN:  
				return anim_man2_digdown;		
			case Logic.MAN2_PUSHLEFT:  
				return anim_man2_pushleft;		
			case Logic.MAN2_PUSHRIGHT:  
				return anim_man2_pushright;		
			case Logic.MAN2_PUSHUP:  
				return anim_man2_pushup;		
			case Logic.MAN2_PUSHDOWN:  
				return anim_man2_pushdown;		
				
			case Logic.LORRYRIGHT:
				return anim_lorry_right;
			case Logic.LORRYLEFT:
				return anim_lorry_left;
			case Logic.LORRYUP:
				return anim_lorry_up;
			case Logic.LORRYDOWN:
				return anim_lorry_down;
			case Logic.BUGRIGHT:
				return anim_bug_right;
			case Logic.BUGLEFT:
				return anim_bug_left;
			case Logic.BUGUP:
				return anim_bug_up;
			case Logic.BUGDOWN:
				return anim_bug_down;
		}
		
		
		// when no animation is explicitly defined, use the still-stand animation of the new piece (covers many "falling" and "pushing" actions)
		return piece2tile[newpiece & 0xff];
	}
	
			
	private int[] determineTransformAnimation(byte oldpiece, byte newpiece, int originatingx, int originatingy, Logic logic)
	{
		switch (oldpiece)
		{	case Logic.ROCK:
			case Logic.ROCK_FALLING: 	
				if (newpiece==Logic.SAND_FULL)
				{	return anim_sand;				
				}
				break;
			case Logic.ROCKEMERALD:
			case Logic.ROCKEMERALD_FALLING: 	
				if (newpiece==Logic.SAND_FULLEMERALD)
				{	return anim_sand;				
				}
				break;
			case Logic.BAG:
			case Logic.BAG_FALLING:
			case Logic.BAG_OPENING:				
				if (newpiece==Logic.EMERALD)
				{	return anim_bag_opening;				
				}
				break;
			case Logic.EMERALD:
				if (newpiece==Logic.AIR) 
				{	return anim_emerald_away;		
				}
				break;
			case Logic.SAPPHIRE:
				if (newpiece==Logic.AIR) 
				{	return anim_sapphire_away;		
				}
				break;				
			case Logic.SAPPHIRE_BREAKING:
				if (newpiece==Logic.AIR)
				{	return anim_sapphire_break;
				}
				break;				
			case Logic.CITRINE:
				if (newpiece==Logic.AIR) 
				{	return anim_citrine_away;		
				}
				break;				
			case Logic.CITRINE_BREAKING:
				if (newpiece==Logic.AIR)
				{	return anim_citrine_break;
				}
				break;				
			case Logic.RUBY:
				if (newpiece==Logic.AIR) 
				{	return anim_ruby_away;		
				}
				break;				
			case Logic.TIMEBOMB:
				if (newpiece==Logic.AIR) 
				{	return anim_timebomb_away;		
				}
				break;				
			case Logic.TIMEBOMB10:
				if (newpiece==Logic.AIR) 
				{	return anim_timebomb10_away;		
				}
				break;				
			case Logic.KEYRED:
				if (newpiece==Logic.AIR) 
				{	return anim_keyred_away;		
				}
				break;				
			case Logic.KEYBLUE:
				if (newpiece==Logic.AIR) 
				{	return anim_keyblue_away;		
				}
				break;				
			case Logic.KEYGREEN:
				if (newpiece==Logic.AIR) 
				{	return anim_keygreen_away;		
				}
				break;				
			case Logic.KEYYELLOW:
				if (newpiece==Logic.AIR) 
				{	return anim_keyyellow_away;		
				}
				break;				

			case Logic.EARTH_UP:	
				return anim_earth_up[earthJaggedConfiguration(logic,originatingx,originatingy)];
			case Logic.EARTH_DOWN:	
				return anim_earth_down[earthJaggedConfiguration(logic,originatingx,originatingy)];
			case Logic.EARTH_LEFT:	
				return anim_earth_left[earthJaggedConfiguration(logic,originatingx,originatingy)];
			case Logic.EARTH_RIGHT:	
				return anim_earth_right[earthJaggedConfiguration(logic,originatingx,originatingy)];

			case Logic.LORRYLEFT:
			case Logic.LORRYLEFT_FIXED:
				if (newpiece==Logic.LORRYUP || newpiece==Logic.LORRYUP_FIXED)
				{	return anim_lorry_left_up;
				}
				else if (newpiece==Logic.LORRYDOWN || newpiece==Logic.LORRYDOWN_FIXED)
				{	return anim_lorry_left_down;
				}
				break;
			case Logic.LORRYRIGHT:
			case Logic.LORRYRIGHT_FIXED:
				if (newpiece==Logic.LORRYDOWN || newpiece==Logic.LORRYDOWN_FIXED)
				{	return anim_lorry_right_down;
				}
				else if (newpiece==Logic.LORRYUP || newpiece==Logic.LORRYUP_FIXED)
				{	return anim_lorry_right_up;
				}
				break;
			case Logic.LORRYDOWN:
			case Logic.LORRYDOWN_FIXED:
				if (newpiece==Logic.LORRYLEFT || newpiece==Logic.LORRYLEFT_FIXED)
				{	return anim_lorry_down_left;
				}
				else if (newpiece==Logic.LORRYRIGHT || newpiece==Logic.LORRYRIGHT_FIXED)
				{	return anim_lorry_down_right;
				}
				break;
			case Logic.LORRYUP:
			case Logic.LORRYUP_FIXED:
				if (newpiece==Logic.LORRYLEFT || newpiece==Logic.LORRYLEFT_FIXED)
				{	return anim_lorry_up_left;
				}
				else if (newpiece==Logic.LORRYRIGHT || newpiece==Logic.LORRYRIGHT_FIXED)
				{	return anim_lorry_up_right;
				}
				break;

			case Logic.BUGLEFT:
			case Logic.BUGLEFT_FIXED:
				if (newpiece==Logic.BUGUP || newpiece==Logic.BUGUP_FIXED)
				{	return anim_bug_left_up;
				}
				else if (newpiece==Logic.BUGDOWN || newpiece==Logic.BUGDOWN_FIXED)
				{	return anim_bug_left_down;
				}
				break;
			case Logic.BUGRIGHT:
			case Logic.BUGRIGHT_FIXED:
				if (newpiece==Logic.BUGDOWN || newpiece==Logic.BUGDOWN_FIXED)
				{	return anim_bug_right_down;
				}
				else if (newpiece==Logic.BUGUP || newpiece==Logic.BUGUP_FIXED)
				{	return anim_bug_right_up;
				}
				break;
			case Logic.BUGDOWN:
			case Logic.BUGDOWN_FIXED:
				if (newpiece==Logic.BUGLEFT || newpiece==Logic.BUGLEFT_FIXED)
				{	return anim_bug_down_left;
				}
				else if (newpiece==Logic.BUGRIGHT || newpiece==Logic.BUGRIGHT_FIXED)
				{	return anim_bug_down_right;
				}
				break;
			case Logic.BUGUP:
			case Logic.BUGUP_FIXED:
				if (newpiece==Logic.BUGLEFT || newpiece==Logic.BUGLEFT_FIXED)
				{	return anim_bug_up_left;
				}
				else if (newpiece==Logic.BUGRIGHT || newpiece==Logic.BUGRIGHT_FIXED)
				{	return anim_bug_up_right;
				}
				break;


			case Logic.EXPLODE1_AIR:  
				return anim_explode1_air;
			case Logic.EXPLODE2_AIR:  
				return anim_explode2_air;
			case Logic.EXPLODE3_AIR:  
				return anim_explode3_air;
			case Logic.EXPLODE4_AIR:  
				return anim_explode4_air;
				
			case Logic.EXPLODE1_EMERALD:  
				return anim_explode1_air;
			case Logic.EXPLODE2_EMERALD:  
				return anim_explode2_air;
			case Logic.EXPLODE3_EMERALD:  
				return anim_explode3_emerald;
			case Logic.EXPLODE4_EMERALD:  
				return anim_explode4_emerald;

			case Logic.EXPLODE1_SAPPHIRE:  
				return anim_explode1_air;
			case Logic.EXPLODE2_SAPPHIRE:  
				return anim_explode2_air;
			case Logic.EXPLODE3_SAPPHIRE:  
				return anim_explode3_sapphire;
			case Logic.EXPLODE4_SAPPHIRE:  
				return anim_explode4_sapphire;

			case Logic.EXPLODE1_RUBY:  
				return anim_explode1_air;
			case Logic.EXPLODE2_RUBY:  
				return anim_explode2_air;
			case Logic.EXPLODE3_RUBY:  
				return anim_explode3_ruby;
			case Logic.EXPLODE4_RUBY:  
				return anim_explode4_ruby;

			case Logic.EXPLODE1_BAG:  
				return anim_explode1_air;
			case Logic.EXPLODE2_BAG:  
				return anim_explode2_air;
			case Logic.EXPLODE3_BAG:  
				return anim_explode3_bag;
			case Logic.EXPLODE4_BAG:  
				return anim_explode4_bag;

			case Logic.EXPLODE1_TNT:  
				return anim_explode1_tnt;
			case Logic.EXPLODE2_TNT:  
				return anim_explode2_tnt;
			case Logic.EXPLODE3_TNT:  
				return anim_explode3_tnt;
			case Logic.EXPLODE4_TNT:  
				return anim_explode4_tnt;
		}
				
		switch (newpiece)
		{
			case Logic.ACTIVEBOMB5:
				if (oldpiece==Logic.AIR)
				{	return anim_timebomb_placement;	
				}
				break;
			case Logic.DROP:
				if (oldpiece==Logic.AIR)
				{	return anim_createdrop;
				}
				else if (oldpiece==Logic.SWAMP_LEFT)
				{	return anim_drop_left;
				}
				else if (oldpiece==Logic.SWAMP_RIGHT)
				{	return anim_drop_right;
				}
				break;
			case Logic.SWAMP_UP:
				return  anim_swamp_up;
			case Logic.SWAMP_DOWN:
				return (oldpiece==Logic.EARTH) ? anim_swamp_down : null;
			case Logic.SWAMP_LEFT:
				return (oldpiece==Logic.EARTH) ? anim_swamp_left : null;
			case Logic.SWAMP_RIGHT:
				return (oldpiece==Logic.EARTH) ? anim_swamp_right : null;
			case Logic.SWAMP:
				if (oldpiece==Logic.DROP)
				{	return anim_drophit;				
				}
				break;
			case Logic.DOOR_OPENED:				
				if (oldpiece==Logic.DOOR)
				{	return anim_door_opening;
				}
				else
				{	return null;
				}
			case Logic.DOOR_CLOSING:
				return null;				
			case Logic.DOOR_CLOSED:
				return anim_door_closing;	

			case Logic.ONETIMEDOOR_CLOSED:
				return anim_door_onetime;

			case Logic.MAN1_DIGLEFT:  
				return anim_man1_digleft;		
			case Logic.MAN1_DIGRIGHT:  
				return anim_man1_digright;		
			case Logic.MAN1_DIGUP:  
				return anim_man1_digup;		
			case Logic.MAN1_DIGDOWN:  
				return anim_man1_digdown;		
			case Logic.MAN1_PUSHLEFT:  
				return anim_man1_pushleft;		
			case Logic.MAN1_PUSHRIGHT:  
				return anim_man1_pushright;		
			case Logic.MAN1_PUSHUP:  
				return anim_man1_pushup;		
			case Logic.MAN1_PUSHDOWN:  
				return anim_man1_pushdown;		
			case Logic.MAN2_DIGLEFT:  
				return anim_man2_digleft;		
			case Logic.MAN2_DIGRIGHT:  
				return anim_man2_digright;		
			case Logic.MAN2_DIGUP:  
				return anim_man2_digup;		
			case Logic.MAN2_DIGDOWN:  
				return anim_man2_digdown;		
			case Logic.MAN2_PUSHLEFT:  
				return anim_man2_pushleft;		
			case Logic.MAN2_PUSHRIGHT:  
				return anim_man2_pushright;		
			case Logic.MAN2_PUSHUP:  
				return anim_man2_pushup;		
			case Logic.MAN2_PUSHDOWN:  
				return anim_man2_pushdown;
				
			case Logic.CUSHION:
				if (oldpiece==Logic.CUSHION_BUMPING)
				{	return null;
				}
				break;
			case Logic.CUSHION_BUMPING:
				if (oldpiece==Logic.CUSHION)
				{	return anim_cushion;
				}
				break;
				
			case Logic.EXPLODE1_AIR:
			case Logic.EXPLODE1_EMERALD:
			case Logic.EXPLODE1_SAPPHIRE:
			case Logic.EXPLODE1_RUBY:
			case Logic.EXPLODE1_BAG:
				return anim_explode0_air;
			case Logic.EXPLODE1_TNT:
				return anim_explode0_tnt;
			
		}
	
		// when no animation is explicitly defined, use the still-stand animation of the new piece (covers many "falling" and "pushing" actions)
		return piece2tile[newpiece & 0xff];	
	}


	private int[] determineHighlightAnimation(byte highlightpiece, int originatingx, int originatingy, Logic logic)
	{
		switch (highlightpiece)
		{	case Logic.EARTH:
				return earthtiles[earthJaggedConfiguration(logic, originatingx, originatingy)];
		
			case Logic.EMERALD:
				return anim_emerald_shine;
			case Logic.SAPPHIRE:
				return anim_sapphire_shine;
			case Logic.RUBY:
				return anim_ruby_shine;
			case Logic.CITRINE:
				return anim_citrine_shine;
			case Logic.MAN1:
				return anim_man1_blink;
			case Logic.MAN2:
				return anim_man2_blink;
		
			case Logic.LASER_V:
				return anim_laser_v;
			case Logic.LASER_H:
				return anim_laser_h;
			case Logic.LASER_BL:
				return anim_laser_bl;
			case Logic.LASER_BR:
				return anim_laser_br;
			case Logic.LASER_TL:
				return anim_laser_tl;
			case Logic.LASER_TR:
				return anim_laser_tr;
			case Logic.LASER_L:
				return anim_laser_left;		
			case Logic.LASER_R:
				return anim_laser_right;		
			case Logic.LASER_U:
				return anim_laser_up;		
			case Logic.LASER_D:
				return anim_laser_down;		
			case Logic.DOORBLUE:
				return anim_door_blue;
			case Logic.DOORGREEN:
				return anim_door_green;
			case Logic.DOORYELLOW:
				return anim_door_yellow;
			case Logic.DOORRED:
				return anim_door_red;	
			case Logic.CUSHION:
				return anim_cushion;					
			case Logic.SWAMP:
				return anim_swamp;
			case Logic.GUN0:
			case Logic.GUN1:
			case Logic.GUN2:
			case Logic.GUN3:
				return anim_gunfire;
				
			case Logic.YAMYAMLEFT:			
			case Logic.YAMYAMRIGHT:			
			case Logic.YAMYAMUP:			
			case Logic.YAMYAMDOWN:			
				return anim_yamyam;

			case Logic.CUSHION_BUMPING:
				return anim_cushion;				
				
			case Logic.CONVERTER:
				return piece2tile[Logic.CONVERTER];
				
			case Logic.DOOR_OPENED:
				return piece2tile[Logic.DOOR_OPENED&0xff];
				
			case Logic.ELEVATOR_TOLEFT:
				return anim_elevatorleft_throw;
			case Logic.ELEVATOR_TORIGHT:
				return anim_elevatorright_throw;
		}
		
		return null;		// no default for highlight	
	}
	
	private int[] determineTileAt(Logic logic, int x, int y)
	{	
		// various appearances of the earth piece
		int p = logic.piece(x,y) & 0xff;
		switch (p)
		{	case Logic.EARTH:
			{	return earthtiles[earthJaggedConfiguration(logic,x,y)];			
			}
			case Logic.WALL:
			{	int c=0;
				byte p2 = logic.piece(x-1,y);
				if (p2==Logic.WALL)
				{	c++;
				}
				else if (p2==Logic.ROUNDWALL)
				{	c+=2;
				}
				p2 = logic.piece(x+1,y);
				if (p2==Logic.WALL)
				{	c+=3;
				}
				else if (p2==Logic.ROUNDWALL)
				{
					c+=6;
				}
				return walltiles[c];
			}
			case Logic.ROUNDWALL:
			{	int c=0;
				int pl = logic.piece(x-1,y);
				int pr = logic.piece(x+1,y);
				if (pl==Logic.WALL || pl==Logic.ROUNDWALL)
				{	c++;
				}
				if (pr==Logic.WALL || pr==Logic.ROUNDWALL)
				{	c+=2;
				}
				return roundwalltiles[c];
			}
			case Logic.ACID:
			{
				int n = logic.getTurnsDone()&1;
				int pl = logic.piece(x-1,y);
				int pr = logic.piece(x+1,y);
				if (pl==Logic.ACID)
				{	if (pr==Logic.ACID)
					{	return acidtiles_noedge[n];
					}
					else
					{	return acidtiles_rightedge[n];
					}
				}
				else
				{	if (pr==Logic.ACID)
					{	return acidtiles_leftedge[n];
					}
					else
					{	return acidtiles_bothedges[n];
					}				
				}
			}
		}
		
	 	// default handling of resting pieces
		return piece2tile[p];		
	}
	
	static int earthJaggedConfiguration(Logic logic, int x, int y)
	{
		if (makesEarthEdgeJagged(logic.piece(x,y-1)))
		{	if (makesEarthEdgeJagged(logic.piece(x,y+1))) 						
			{	if (makesEarthEdgeJagged(logic.piece(x-1,y))) 						
				{	return makesEarthEdgeJagged(logic.piece(x+1,y)) ? 0:4; 						
				}
				else
				{	return makesEarthEdgeJagged(logic.piece(x+1,y)) ? 3:10; 						
				}
			}
			else
			{	if (makesEarthEdgeJagged(logic.piece(x-1,y))) 						
				{	return makesEarthEdgeJagged(logic.piece(x+1,y)) ? 2:9; 						
				}
				else
				{	return makesEarthEdgeJagged(logic.piece(x+1,y)) ? 8:14; 						
				}
			}
		}
		else
		{	if (makesEarthEdgeJagged(logic.piece(x,y+1))) 						
			{	if (makesEarthEdgeJagged(logic.piece(x-1,y))) 						
				{	return makesEarthEdgeJagged(logic.piece(x+1,y)) ? 1:7; 						
				}
				else
				{	return makesEarthEdgeJagged(logic.piece(x+1,y)) ? 6:13; 						
				}
			}
			else
			{	if (makesEarthEdgeJagged(logic.piece(x-1,y))) 						
				{	return makesEarthEdgeJagged(logic.piece(x+1,y)) ? 5:12; 						
				}
				else
				{	return makesEarthEdgeJagged(logic.piece(x+1,y)) ? 11:15; 						
				}
			}
		}				
	}
	
	static boolean makesEarthEdgeJagged(int piece)
	{
		switch (piece)
		{	case Logic.EARTH: 
			case Logic.WALL:  
			case Logic.STONEWALL: 
			case Logic.GLASSWALL: 
			case Logic.WALLEMERALD:
			case Logic.SWAMP_UP:
			case Logic.SWAMP_DOWN:
			case Logic.SWAMP_LEFT:
			case Logic.SWAMP_RIGHT:			
			case Logic.MAN1_DIGLEFT:
			case Logic.MAN2_DIGLEFT:
			case Logic.MAN1_DIGRIGHT:
			case Logic.MAN2_DIGRIGHT:
			case Logic.MAN1_DIGUP:
			case Logic.MAN2_DIGUP:
			case Logic.MAN1_DIGDOWN:
			case Logic.MAN2_DIGDOWN:	return false;			
			default:			return true;
		}
	}

	// -------------- loading of all the tiles and creation of the animation descriptors ------------
	
	private void loadTiles(Context context)
	{
		LoadingState h = new LoadingState(context);
		
		piece2tile[Logic.MAN1] = loadImage(h, "1man");
		anim_man1_blink = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN2] = loadImage(h, "2man");		
		anim_man2_blink = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN1_LEFT&0xff] = loadImage(h, "1walklft");
		anim_man1_left = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN1_RIGHT&0xff] = loadImage(h, "1walkrgt");
		anim_man1_right = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN1_UP&0xff] = loadImage(h, "1walkup");
		anim_man1_up = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN1_DOWN&0xff] = loadImage(h, "1walkdwn");
		anim_man1_down = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN1_DIGLEFT&0xff] = loadImage(h, "1diglft");
		anim_man1_digleft = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN1_DIGRIGHT&0xff] = loadImage(h, "1digrgt");
		anim_man1_digright = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN1_DIGUP&0xff] = loadImage(h, "1digup");
		anim_man1_digup = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN1_DIGDOWN&0xff] = loadImage(h, "1digdwn");
		anim_man1_digdown = createAnimationDescription(h.first,h.last);
		loadImage(h, "1pushlft");
		piece2tile[Logic.MAN1_PUSHLEFT&0xff] = anim_man1_pushleft = createAnimationDescription(h.first,h.last);
		loadImage(h, "1pushrgt");
		piece2tile[Logic.MAN1_PUSHRIGHT&0xff] = anim_man1_pushright = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN1_PUSHUP&0xff] = anim_man1_pushup = anim_man1_up;
		piece2tile[Logic.MAN1_PUSHDOWN&0xff] = anim_man1_pushdown = anim_man1_down;
		     		
		piece2tile[Logic.MAN2_LEFT&0xff] = loadImage(h, "2walklft");
		anim_man2_left = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN2_RIGHT&0xff] = loadImage(h, "2walkrgt");
		anim_man2_right = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN2_UP&0xff] = loadImage(h, "2walkup");
		anim_man2_up = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN2_DOWN&0xff] = loadImage(h, "2walkdwn");
		anim_man2_down = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN2_DIGLEFT&0xff] = loadImage(h, "2diglft");
		anim_man2_digleft = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN2_DIGRIGHT&0xff] = loadImage(h, "2digrgt");
		anim_man2_digright = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN2_DIGUP&0xff] = loadImage(h, "2digup");
		anim_man2_digup = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN2_DIGDOWN&0xff] = loadImage(h, "2digdwn");
		anim_man2_digdown = createAnimationDescription(h.first,h.last);
		loadImage(h, "2pushlft");
		piece2tile[Logic.MAN2_PUSHLEFT&0xff] = anim_man2_pushleft = createAnimationDescription(h.first,h.last);
		loadImage(h, "2pushrgt");
		piece2tile[Logic.MAN2_PUSHRIGHT&0xff] = anim_man2_pushright = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.MAN2_PUSHUP&0xff] = anim_man2_pushup = anim_man2_up;
		piece2tile[Logic.MAN2_PUSHDOWN&0xff] = anim_man2_pushdown = anim_man2_down;
				
		piece2tile[Logic.EARTH] = loadImage(h, "Earth All");    
		for (int i=0; i<16; i++) 
		{	earthtiles[i] = createAnimationDescription(h.first+i);
		}				
		piece2tile[Logic.WALL] = loadImage(h, "Wall All");
		walltiles[0] = createAnimationDescription(h.first+5);  // nothing - wall - nothing		
		walltiles[1] = createAnimationDescription(h.first+6);  // wall    - wall - nothing
		walltiles[2] = createAnimationDescription(h.first+2);  // rounded - wall - nothing  		
		walltiles[3] = createAnimationDescription(h.first+7);  // nothing - wall - wall  		
		walltiles[4] = createAnimationDescription(h.first+8);  // wall    - wall - wall  		
		walltiles[5] = createAnimationDescription(h.first+0);  // rounded - wall - wall     ??    		
		walltiles[6] = createAnimationDescription(h.first+3);  // nothing - wall - rounded  		
		walltiles[7] = createAnimationDescription(h.first+1);  // wall    - wall - rounded  ??    		
		walltiles[8] = createAnimationDescription(h.first+4);  // rounded - wall - rounded  		
		piece2tile[Logic.ROUNDWALL] = loadImage(h, "Wall Round All");    		
		for (int i=0; i<4; i++) 
		{	roundwalltiles[i] = createAnimationDescription(h.first+i);
		}				
		anim_earth_right = new int[16][];
		loadImage(h,"Earth Right");
		int[] anim_removal = createAnimationDescription(h.first,h.last); 
		for (int i=0; i<16; i++) anim_earth_right[i] = joinAnimationDescriptions(earthtiles[i], anim_removal);		
		anim_earth_up = new int[16][];
		anim_removal = createRotatedAnimation(anim_removal, 90);
		for (int i=0; i<16; i++) anim_earth_up[i] = joinAnimationDescriptions(earthtiles[i], anim_removal);
		anim_earth_left = new int[16][];
		anim_removal = createRotatedAnimation(anim_removal, 90);
		for (int i=0; i<16; i++) anim_earth_left[i] = joinAnimationDescriptions(earthtiles[i], anim_removal);		
		anim_earth_down = new int[16][];
		anim_removal = createRotatedAnimation(anim_removal, 90);
		for (int i=0; i<16; i++) anim_earth_down[i] = joinAnimationDescriptions(earthtiles[i], anim_removal);		
		
		piece2tile[Logic.SAND] = loadImage(h, "Sand");
		piece2tile[Logic.GLASSWALL] = loadImage(h, "Glass");
		piece2tile[Logic.STONEWALL] = loadImage(h, "Stone Wall");
		piece2tile[Logic.ROUNDSTONEWALL] = loadImage(h, "Round Stone Wall");
		piece2tile[Logic.WALLEMERALD] = loadImage(h, "Wall Emerald");
		piece2tile[Logic.EMERALD] = piece2tile[Logic.EMERALD_FALLING&0xff] = loadImage(h, "Emerald");
		anim_emerald_fall = createAnimationDescription(h.first,h.last);
		loadImage(h, "Emerald Shine");
		anim_emerald_shine = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.CITRINE] = piece2tile[Logic.CITRINE_FALLING&0xff] = loadImage(h, "Citrine");
		anim_citrine_fall = createAnimationDescription(h.first,h.last);
		loadImage(h, "Citrine Shine");
		anim_citrine_shine = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.SAPPHIRE] = piece2tile[Logic.SAPPHIRE_FALLING&0xff] = loadImage(h, "Sapphire");
		anim_sapphire_fall = createAnimationDescription(h.first,h.last);
		loadImage(h, "Sapphire Shine");
		anim_sapphire_shine = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.RUBY] = piece2tile[Logic.RUBY_FALLING&0xff] = loadImage(h, "Ruby");
		anim_ruby_fall = createAnimationDescription(h.first,h.last);
		loadImage(h, "Ruby Shine");
		anim_ruby_shine = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.ROCK] = piece2tile[Logic.ROCK_FALLING&0xff] = loadImage(h, "Stone"); 
		loadImage(h, "Stone Right");
		anim_rock_right = createAnimationDescription(h.first,h.last);
		loadImage(h, "Stone Left");
		anim_rock_left = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.ROCKEMERALD] = piece2tile[Logic.ROCKEMERALD_FALLING&0xff] = loadImage(h, "Stone Emerald");
		anim_rockemerald_right = createAnimationDescription(h.first,h.last);
		anim_rockemerald_left = createRevertedAnimation(anim_rockemerald_right);
		piece2tile[Logic.BAG] = piece2tile[Logic.BAG_FALLING&0xff] = loadImage(h, "Bag");
		anim_bag_right = createAnimationDescription(h.first,h.last);
		anim_bag_left = createAnimationDescription(h.last,h.first);
		piece2tile[Logic.BOMB] = loadImage(h, "Bomb"); 
		loadImage(h, "Bomb Push");
		anim_bomb_left = createAnimationDescription(h.first,h.last);
		anim_bomb_right = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.BOMB_FALLING&0xff] = loadImage(h, "Bomb Falling");
		anim_bomb_fall = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.DOOR&0xff] = piece2tile[Logic.DOOR_CLOSED&0xff] = loadImage(h, "Exit Closed");    
		piece2tile[Logic.DOOR_OPENED&0xff] = piece2tile[Logic.DOOR_CLOSING&0xff] = loadImage(h, "Exit");  
		anim_door_opening = createAnimationDescription(h.first,h.last); 
		anim_door_closing = createRevertedAnimation(anim_door_opening);
		piece2tile[Logic.SWAMP] = loadImage(h, "Swamp");
		piece2tile[Logic.SWAMP_UP & 0xff] = piece2tile[Logic.SWAMP];
		piece2tile[Logic.SWAMP_DOWN & 0xff] = piece2tile[Logic.SWAMP];
		piece2tile[Logic.SWAMP_LEFT & 0xff] = piece2tile[Logic.SWAMP];
		piece2tile[Logic.SWAMP_RIGHT & 0xff] = piece2tile[Logic.SWAMP];
		loadImage(h, "Swamp Move");
		anim_swamp = createAnimationDescription(h.first, h.last);
		loadImage(h, "Swamp Grow");
		anim_swamp_up = createAnimationDescription(h.first, h.last);
		anim_swamp_left = createRotatedAnimation(anim_swamp_up, 90);
		anim_swamp_right = createRotatedAnimation(anim_swamp_up, -90);		
		anim_swamp_down = createRotatedAnimation(anim_swamp_up, -180);
		loadImage(h, "Drop Left");
		anim_drop_left = createAnimationDescription(h.first, h.last);
		loadImage(h, "Drop Right");
		anim_drop_right = createAnimationDescription(h.first, h.last);
		loadImage(h, "Drop Down");
		anim_createdrop = createAnimationDescription(h.first, h.last);
		loadImage(h, "Drop Hit");
		anim_drophit = createAnimationDescription(h.first, h.last);		
		piece2tile[Logic.DROP] = loadImage(h, "Drop");  // loadImage(h, "drop");
		anim_drop = createAnimationDescription(h.first, h.last);
		loadImage(h, "Converter");
		piece2tile[Logic.CONVERTER] = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.TIMEBOMB] = piece2tile[Logic.ACTIVEBOMB4&0xff] = piece2tile[Logic.ACTIVEBOMB2&0xff] = piece2tile[Logic.ACTIVEBOMB0&0xff] = loadImage(h, "Timebomb");
		piece2tile[Logic.ACTIVEBOMB5&0xff] = piece2tile[Logic.ACTIVEBOMB3&0xff] = piece2tile[Logic.ACTIVEBOMB1&0xff] = loadImage(h, "Tickbomb");
		piece2tile[Logic.TIMEBOMB10] = loadImage(h, "TNT");
		piece2tile[Logic.BOX] = loadImage(h, "Safe");
		piece2tile[Logic.CUSHION] = loadImage(h, "Pillow");
		anim_cushion = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.ELEVATOR] = loadImage(h, "Elevator");
		anim_elevator = createAnimationDescription(h.last,h.first);
		piece2tile[Logic.ELEVATOR_TOLEFT] = loadImage(h, "Elevator Left");
		anim_elevatorleft = createAnimationDescription(h.last, h.first);
		piece2tile[Logic.ELEVATOR_TORIGHT] = loadImage(h, "Elevator Right");
		anim_elevatorright = createAnimationDescription(h.last, h.first);
		loadImage(h, "Elevator Left Throw");
		anim_elevatorleft_throw = createAnimationDescription(h.first,h.last);
		loadImage(h, "Elevator Right Throw");
		anim_elevatorright_throw = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.GUN0] = loadImage(h, "Gun");
		piece2tile[Logic.GUN1] = piece2tile[Logic.GUN0];
		piece2tile[Logic.GUN2] = piece2tile[Logic.GUN0];
		piece2tile[Logic.GUN3] = piece2tile[Logic.GUN0];
		loadImage(h, "Gun Fire");
		anim_gunfire = createAnimationDescription(h.first,h.last);
		loadImage(h, "Acid");
		piece2tile[Logic.ACID] = createAnimationDescription(h.first,h.last); 
		acidtiles_noedge[0] = createAnimationDescription(h.first, (h.first+h.last)/2);
		acidtiles_noedge[1] = createAnimationDescription((h.first+h.last)/2, h.last);
		loadImage(h, "Acid Edge Left");
		acidtiles_leftedge[0] = joinAnimationDescriptions(acidtiles_noedge[0], createAnimationDescription(h.first,h.last));
		acidtiles_leftedge[1] = joinAnimationDescriptions(acidtiles_noedge[1], createAnimationDescription(h.first,h.last));
		loadImage(h, "Acid Edge Right");
		acidtiles_rightedge[0] = joinAnimationDescriptions(acidtiles_noedge[0], createAnimationDescription(h.first,h.last));
		acidtiles_rightedge[1] = joinAnimationDescriptions(acidtiles_noedge[1], createAnimationDescription(h.first,h.last));
		loadImage(h, "Acid Edge Both");
		acidtiles_bothedges[0] = joinAnimationDescriptions(acidtiles_noedge[0], createAnimationDescription(h.first,h.last));
		acidtiles_bothedges[1] = joinAnimationDescriptions(acidtiles_noedge[1], createAnimationDescription(h.first,h.last));
		piece2tile[Logic.KEYBLUE] = loadImage(h, "Key Blue");
		piece2tile[Logic.KEYRED] = loadImage(h, "Key Red");
		piece2tile[Logic.KEYGREEN] = loadImage(h, "Key Green");
		piece2tile[Logic.KEYYELLOW] = loadImage(h, "Key Yellow");
		piece2tile[Logic.DOORBLUE] = loadImage(h, "Door Blue");
		anim_door_blue = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.DOORRED] = loadImage(h, "Door Red");
		anim_door_red = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.DOORGREEN] = loadImage(h, "Door Green");
		anim_door_green = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.DOORYELLOW] = loadImage(h, "Door Yellow");
		anim_door_yellow = createAnimationDescription(h.first,h.last);
		
		loadImage(h, "Door Onetime");
		piece2tile[Logic.ONETIMEDOOR] = createAnimationDescription(h.last);
		anim_door_onetime = createAnimationDescription(h.last,h.first);
		piece2tile[Logic.ONETIMEDOOR_CLOSED&0xff] = loadImage(h, "Door Onetime Closed");	
		
		loadImage(h, "Lorry");
		anim_lorry_left = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.LORRYLEFT] = piece2tile[Logic.LORRYLEFT_FIXED&0xff] = anim_lorry_left;
		anim_lorry_down =  createRotatedAnimation(anim_lorry_left,90);
		piece2tile[Logic.LORRYDOWN] = piece2tile[Logic.LORRYDOWN_FIXED&0xff] = anim_lorry_down;
		anim_lorry_right = createRotatedAnimation(anim_lorry_left,180);
		piece2tile[Logic.LORRYRIGHT] = piece2tile[Logic.LORRYRIGHT_FIXED&0xff] = anim_lorry_right;
 		anim_lorry_up = createRotatedAnimation(anim_lorry_left,270);		 
		piece2tile[Logic.LORRYUP] = piece2tile[Logic.LORRYUP_FIXED&0xff] = anim_lorry_up;
		anim_lorry_right_up = createRotatingAnimation(anim_lorry_right, 0,90);
		anim_lorry_up_left = createRotatingAnimation(anim_lorry_right, 90,180);
		anim_lorry_left_down = createRotatingAnimation(anim_lorry_right, 180,270);
		anim_lorry_down_right = createRotatingAnimation(anim_lorry_right, 270,360);
		anim_lorry_right_down = createRotatingAnimation(anim_lorry_right, 0,-90);
		anim_lorry_down_left = createRotatingAnimation(anim_lorry_right, 270,180);
		anim_lorry_left_up = createRotatingAnimation(anim_lorry_right, 180, 90);
		anim_lorry_up_right = createRotatingAnimation(anim_lorry_right, 90, 0);
		
		loadImage(h, "Bug");
		anim_bug_right = createAnimationDescription(h.first,h.last);
		piece2tile[Logic.BUGRIGHT] = piece2tile[Logic.BUGRIGHT_FIXED&0xff] = anim_bug_right;
		anim_bug_up =  createRotatedAnimation(anim_bug_right,90);
		piece2tile[Logic.BUGUP] = piece2tile[Logic.BUGUP_FIXED&0xff] = anim_bug_up;
		anim_bug_left =  createRotatedAnimation(anim_bug_right,180);
		piece2tile[Logic.BUGLEFT] = piece2tile[Logic.BUGLEFT_FIXED&0xff] = anim_bug_left;
		anim_bug_down =  createRotatedAnimation(anim_bug_right,270);				
		piece2tile[Logic.BUGDOWN] = piece2tile[Logic.BUGDOWN_FIXED&0xff] = anim_bug_down;
		anim_bug_right_up = createRotatingAnimation(anim_bug_right, 0,90);
		anim_bug_up_left = createRotatingAnimation(anim_bug_right, 90,180);
		anim_bug_left_down = createRotatingAnimation(anim_bug_right, 180,270);
		anim_bug_down_right = createRotatingAnimation(anim_bug_right, 270,360);
		anim_bug_right_down = createRotatingAnimation(anim_bug_right, 0,-90);
		anim_bug_down_left = createRotatingAnimation(anim_bug_right, 270,180);
		anim_bug_left_up = createRotatingAnimation(anim_bug_right, 180, 90);
		anim_bug_up_right = createRotatingAnimation(anim_bug_right, 90, 0);
		
		piece2tile[Logic.YAMYAMLEFT] = loadImage(h, "YamYam Left");
		piece2tile[Logic.YAMYAMUP] = loadImage(h, "YamYam Up");
		piece2tile[Logic.YAMYAMRIGHT] = loadImage(h, "YamYam Right");
		piece2tile[Logic.YAMYAMDOWN] = loadImage(h, "YamYam Down");
		loadImage (h,"YamYam");
		anim_yamyam = createAnimationDescription(h.first,h.last);	
		piece2tile[Logic.ROBOT] = loadImage(h, "Robot");		

		piece2tile[Logic.SAND_FULL] = joinAnimationDescriptions(piece2tile[Logic.ROCK], piece2tile[Logic.SAND] );    
		piece2tile[Logic.SAND_FULLEMERALD] = joinAnimationDescriptions(piece2tile[Logic.ROCKEMERALD], piece2tile[Logic.SAND] );    
		
		loadImage(h, "Explosion");
		int num = (h.last-h.first)+1;	
		anim_explode0_air = createAnimationDescription(h.first, h.first+num/5-1);
		anim_explode1_air = createAnimationDescription(h.first+num/5, h.first+2*num/5-1);
		anim_explode2_air = createAnimationDescription(h.first+2*num/5, h.first+3*num/5-1);
		anim_explode3_air = createAnimationDescription(h.first+3*num/5, h.first+4*num/5-1);
		anim_explode4_air = createAnimationDescription(h.first+4*num/5, h.first+5*num/5-1);
		anim_explode3_emerald = joinAnimationDescriptions(piece2tile[Logic.EMERALD], anim_explode3_air);
		anim_explode4_emerald = joinAnimationDescriptions(piece2tile[Logic.EMERALD], anim_explode4_air);
		anim_explode3_sapphire = joinAnimationDescriptions(piece2tile[Logic.SAPPHIRE], anim_explode3_air);
		anim_explode4_sapphire = joinAnimationDescriptions(piece2tile[Logic.SAPPHIRE], anim_explode4_air);
		anim_explode3_ruby = joinAnimationDescriptions(piece2tile[Logic.RUBY], anim_explode3_air);
		anim_explode4_ruby = joinAnimationDescriptions(piece2tile[Logic.RUBY], anim_explode4_air);
		anim_explode3_bag = joinAnimationDescriptions(piece2tile[Logic.BAG], anim_explode3_air);
		anim_explode4_bag = joinAnimationDescriptions(piece2tile[Logic.BAG], anim_explode4_air);		
		piece2tile[Logic.BOMB_EXPLODE&0xff]      = anim_explode0_air;    
		piece2tile[Logic.BIGBOMB_EXPLODE&0xff]   = anim_explode0_air;    
		piece2tile[Logic.BUG_EXPLODE&0xff]       = anim_explode0_air;    
		piece2tile[Logic.LORRY_EXPLODE&0xff]     = anim_explode0_air;		
		piece2tile[Logic.TIMEBOMB_EXPLODE&0xff]  = anim_explode0_air;
		piece2tile[Logic.EXPLODE1_AIR&0xff]      = anim_explode0_air;
		piece2tile[Logic.EXPLODE2_AIR&0xff]    	 = anim_explode1_air;  
		piece2tile[Logic.EXPLODE3_AIR&0xff]      = anim_explode2_air;   
		piece2tile[Logic.EXPLODE4_AIR&0xff]      = anim_explode3_air;    
		piece2tile[Logic.EXPLODE1_EMERALD&0xff]  = anim_explode0_air;
		piece2tile[Logic.EXPLODE2_EMERALD&0xff]  = anim_explode1_air;
		piece2tile[Logic.EXPLODE3_EMERALD&0xff]  = anim_explode2_air;
		piece2tile[Logic.EXPLODE4_EMERALD&0xff]  = anim_explode3_emerald;  
		piece2tile[Logic.EXPLODE1_SAPPHIRE&0xff] = anim_explode0_air;    
		piece2tile[Logic.EXPLODE2_SAPPHIRE&0xff] = anim_explode1_air;   
		piece2tile[Logic.EXPLODE3_SAPPHIRE&0xff] = anim_explode2_air; 
		piece2tile[Logic.EXPLODE4_SAPPHIRE&0xff] = anim_explode3_sapphire;
		piece2tile[Logic.EXPLODE1_RUBY&0xff]     = anim_explode0_air;    
		piece2tile[Logic.EXPLODE2_RUBY&0xff]     = anim_explode1_air;   
		piece2tile[Logic.EXPLODE3_RUBY&0xff]     = anim_explode2_air; 
		piece2tile[Logic.EXPLODE4_RUBY&0xff]     = anim_explode3_ruby;
		piece2tile[Logic.EXPLODE1_BAG&0xff]    	 = anim_explode0_air; 
		piece2tile[Logic.EXPLODE2_BAG&0xff]    	 = anim_explode1_air;    
		piece2tile[Logic.EXPLODE3_BAG&0xff]      = anim_explode2_air;    
		piece2tile[Logic.EXPLODE4_BAG&0xff]      = anim_explode3_bag;    				
		loadImage(h, "Explosion Deep");
		num = (h.last-h.first)+1;	
		anim_explode0_tnt = createAnimationDescription(h.first, h.first+num/5-1);
		anim_explode1_tnt = createAnimationDescription(h.first+num/5, h.first+2*num/5-1);
		anim_explode2_tnt = createAnimationDescription(h.first+2*num/5, h.first+3*num/5-1);
		anim_explode3_tnt = createAnimationDescription(h.first+3*num/5, h.first+4*num/5-1);
		anim_explode4_tnt = createAnimationDescription(h.first+4*num/5, h.first+5*num/5-1);
		piece2tile[Logic.EXPLODE1_TNT&0xff]    	 = anim_explode0_tnt; 
		piece2tile[Logic.EXPLODE2_TNT&0xff]    	 = anim_explode1_tnt;    
		piece2tile[Logic.EXPLODE3_TNT&0xff]      = anim_explode2_tnt;    
		piece2tile[Logic.EXPLODE4_TNT&0xff]      = anim_explode3_tnt;    				



		loadImage(h,"Sapphire Break");
		anim_sapphire_break = createAnimationDescription(h.first,h.last);
		loadImage(h,"Citrine Break");
		anim_citrine_break = createAnimationDescription(h.first,h.last);	
		anim_sand = createAnimationDescription(piece2tile[Logic.SAND][0]);
		loadImage (h,"Bag Open");
		anim_bag_opening = createAnimationDescription(h.first,h.last);

		anim_sapphire_away = createShrinkAnimationDescription(piece2tile[Logic.SAPPHIRE][0]);
		anim_emerald_away = createShrinkAnimationDescription(piece2tile[Logic.EMERALD][0]);	
		anim_citrine_away = createShrinkAnimationDescription(piece2tile[Logic.CITRINE][0]);
		anim_ruby_away = createShrinkAnimationDescription(piece2tile[Logic.RUBY][0]);	
		anim_timebomb_away = createShrinkAnimationDescription(piece2tile[Logic.TIMEBOMB][0]);
		anim_timebomb_placement = createRotatingAnimation(createRevertedAnimation(createShrinkAnimationDescription(piece2tile[Logic.ACTIVEBOMB5][0])), 20,0);
		anim_timebomb10_away = createShrinkAnimationDescription(piece2tile[Logic.TIMEBOMB10][0]);
		anim_keyred_away = createShrinkAnimationDescription(piece2tile[Logic.KEYRED][0]);
		anim_keyblue_away = createShrinkAnimationDescription(piece2tile[Logic.KEYBLUE][0]);
		anim_keygreen_away = createShrinkAnimationDescription(piece2tile[Logic.KEYGREEN][0]);
		anim_keyyellow_away = createShrinkAnimationDescription(piece2tile[Logic.KEYYELLOW][0]);

		loadImage(h,"Laser");
		anim_laser_v = createAnimationDescription(h.first,h.last);
		anim_laser_h = createRotatedAnimation(anim_laser_v, 90);
		loadImage(h,"Laser Side");
		anim_laser_br = createAnimationDescription(h.first,h.last);		
		anim_laser_tr = createRotatedAnimation(anim_laser_br, 90);
		anim_laser_tl = createRotatedAnimation(anim_laser_br, 180);
		anim_laser_bl = createRotatedAnimation(anim_laser_br, 270);
		loadImage(h,"Laser Reflect");
		anim_laser_down = createAnimationDescription(h.first,h.last);		
		anim_laser_right = createRotatedAnimation(anim_laser_down, 90);		
		anim_laser_up = createRotatedAnimation(anim_laser_down, 180);		
		anim_laser_left = createRotatedAnimation(anim_laser_down, 270);	
				
//		loadImage (h, "solvedmarkers");
//		tile_solvedmarkers[0] = h.last;
//		tile_solvedmarkers[1] = h.last-1;
//		tile_solvedmarkers[2] = h.last-2;
//		tile_solvedmarkers[3] = h.last-3;
		
		// overwrite some standard objects with experimental ones that contain only of a single image
//		piece2tile[Logic.BOMB] = piece2tile[Logic.BOMB_FALLING&0xff] = loadImage(h,"test/Bomb");
//		piece2tile[Logic.CITRINE] = piece2tile[Logic.CITRINE_FALLING&0xff] = loadImage(h,"test/Citrine");
//		piece2tile[Logic.RUBY] = piece2tile[Logic.RUBY_FALLING&0xff] = loadImage(h,"test/Ruby");
//		piece2tile[Logic.EMERALD] = piece2tile[Logic.EMERALD_FALLING&0xff] = loadImage(h,"test/Emerald");
//		piece2tile[Logic.SAPPHIRE] = piece2tile[Logic.SAPPHIRE_FALLING&0xff] = loadImage(h,"test/Sapphire");
//		piece2tile[Logic.DOORBLUE] = loadImage(h,"test/Doorblue");
//		piece2tile[Logic.ROCKEMERALD] = loadImage(h,"test/StoneEmeraldPLACEHOLDER");
//		piece2tile[Logic.DOORRED] = loadImage(h,"test/Doorred");
//		piece2tile[Logic.DOORYELLOW] = loadImage(h,"test/Dooryellow");
//		piece2tile[Logic.DOORGREEN] = loadImage(h,"test/Doorgreen");
//		piece2tile[Logic.CUSHION] = loadImage(h, "test/Pillow");
//		piece2tile[Logic.KEYBLUE] = loadImage(h,"test/Keyblue");
//		piece2tile[Logic.KEYRED] = loadImage(h,"test/Keyred");
//		piece2tile[Logic.KEYYELLOW] = loadImage(h,"test/Keyyellow");
//		piece2tile[Logic.KEYGREEN] = loadImage(h,"test/Keygreen");		
//		piece2tile[Logic.WALL] = loadImage(h,"test/Wall");
//		piece2tile[Logic.GLASSWALL] = loadImage(h,"test/Glass");
//		piece2tile[Logic.ROBOT] = loadImage(h,"test/Robot");
//		piece2tile[Logic.TIMEBOMB&0xff] = piece2tile[Logic.ACTIVEBOMB4&0xff] = piece2tile[Logic.ACTIVEBOMB2&0xff] = loadImage(h,"test/Timebomb");
//		piece2tile[Logic.ACTIVEBOMB5&0xff] = piece2tile[Logic.ACTIVEBOMB3&0xff] = piece2tile[Logic.ACTIVEBOMB1&0xff] = loadImage(h,"test/Tickbomb");		
		
		System.out.println("Number of tiles: "+ h.readcursor+ " of "+(TILEROWS*TILESPERROW));
	}
	
	private int[] loadImage(LoadingState h, String name)
	{
    	// load the image
    	Bitmap bmp = null;
    	try {
    		InputStream is = h.context.getAssets().open("art/"+name+".png");
    		bmp = BitmapFactory.decodeStream (is);
    		is.close();
    	} catch (IOException e) {
    		setError("Bitmap not found: "+e.toString());
    		return new int[FRAMESPERSTEP];	
    	}
    	if (bmp==null)
    	{	setError("Tile image for "+name+" could not be decoded");
    		return new int[FRAMESPERSTEP];	
    	}
    	int iw = bmp.getWidth();
    	int ih = bmp.getHeight();
    	if (ih<TILEHEIGHT || iw<TILEWIDTH)
    	{	setError("Image is too small: "+name);
    		return new int[FRAMESPERSTEP];	
    	}
     	
    	// memorize the first tile position of current image
    	h.first = h.readcursor;
    	
    	// break the image into individual tiles and revert order while loading (images contain the tiles right-to-left)
    	// additionally doublicate the outmost pixels to get a 1-pixel wide border that have same color as image inside 
    	int num = iw / TILEWIDTH;	// number of tiles in the bitmap
    	
    	// create the buffer into which the tiles are extracted (and the border-pixel is added)
    	for (int i=0; i<num; i++)
    	{	// extract the original tile data without padding
    		bmp.getPixels(h.pixels, 0,TILEWIDTH, (num-1-i)*TILEWIDTH,0, TILEWIDTH,TILEHEIGHT);
    		// compose into the temporary bitmap
    		h.bitmap.setPixels(h.pixels, 0,TILEWIDTH,                        1,1,     TILEWIDTH,TILEHEIGHT);      // center part
    		h.bitmap.setPixels(h.pixels, 0,TILEWIDTH,                        1,0,     TILEWIDTH, 1);              // duplicate top row
    		h.bitmap.setPixels(h.pixels, TILEWIDTH*(TILEHEIGHT-1),TILEWIDTH, 1,TILEHEIGHT+1, TILEWIDTH, 1);       // duplicate bottom row
    		h.bitmap.setPixels(h.pixels, 0,TILEWIDTH,                        0,1, 1,TILEHEIGHT);                  // duplicate left row
    		h.bitmap.setPixels(h.pixels, TILEWIDTH-1,TILEWIDTH,              TILEWIDTH+1,1, 1,TILEHEIGHT);        // duplicate right row
    		h.bitmap.setPixel(0,0,                      h.pixels[0]);                                   // top left corner pixel
    		h.bitmap.setPixel(TILEWIDTH+1,0,            h.pixels[TILEWIDTH]);                           // top right corner pixel
    		h.bitmap.setPixel(0,TILEHEIGHT+1,           h.pixels[TILEWIDTH*(TILEHEIGHT-1)]);            // bottom left corner pixel
    		h.bitmap.setPixel(TILEWIDTH+1,TILEHEIGHT+1, h.pixels[(TILEWIDTH*TILEHEIGHT)-1]);            // bottom right corner pixel
    		    		
    		// transfer into byte buffer
    		h.bytebuffer.clear();
    		h.bitmap.copyPixelsToBuffer (h.bytebuffer);
    		h.bytebuffer.flip();
    		// finally transfer into the correct spot in the opengl texture atlas (if not already filled)
    		if (h.readcursor<ATLASTILES)
    		{	int txrow = h.readcursor / TILESPERROW;
	    		int txcol = h.readcursor % TILESPERROW;
	           	glBindTexture(GL_TEXTURE_2D, txTexture);
	            glTexSubImage2D(GL_TEXTURE_2D,0,txcol*(TILEWIDTH+2),txrow*(TILEHEIGHT+2),TILEWIDTH+2,TILEHEIGHT+2, GL_RGBA,GL_UNSIGNED_BYTE, h.bytebuffer);
    		}
			// increment tile position counter            
            h.readcursor++;
    	}	
    	
    	// update the history
    	h.last = h.readcursor-1;
    	
    	// by default create an animation description with a still image
    	return createAnimationDescription(h.last);
	}

	class LoadingState
	{
		final Context context;
		final Bitmap bitmap;
		final int[] pixels;
		final ByteBuffer bytebuffer;    	
	
		int first;
		int last;
		int readcursor;
		
		LoadingState(Context context)
		{	
			this.context = context;
			pixels = new int[TILEWIDTH*TILEHEIGHT];
			bitmap = Bitmap.createBitmap(TILEWIDTH+2, TILEHEIGHT+2, Bitmap.Config.ARGB_8888);
			bytebuffer = ByteBuffer.allocate(4*(TILEWIDTH+2)*(TILEHEIGHT*2));

			first = 0;
			last = 0;
			readcursor = 0;
	
		}
	}
	
	
	private int[] createAnimationDescription(int firsttile, int finaltile)
	{
		int numtiles = (finaltile-firsttile)+1;
		int[] a = new int[FRAMESPERSTEP];
		for (int i=0; i<a.length; i++)
		{
			a[i] = firsttile + ((FRAMESPERSTEP-1-i)*numtiles) / FRAMESPERSTEP;
		}
		return a;
	}

	private int[] createAnimationDescription(int tile)
	{
		int[] a = new int[FRAMESPERSTEP];
		for (int i=0; i<a.length; i++)
		{	a[i] = tile;
		}
		return a;
	}
	
	private int[] createShrinkAnimationDescription(int tile)
	{
		int[] a = new int[FRAMESPERSTEP];
		for (int i=0; i<a.length; i++)
		{	a[i] = tile + (((60*(FRAMESPERSTEP-i))/FRAMESPERSTEP) << 16);
		}
		return a;
	}

	private int[] joinAnimationDescriptions(int[] x, int[] y)
	{
		int[] a = new int[x.length+y.length];
		System.arraycopy(x,0,a,0, x.length);
		System.arraycopy(y,0,a,x.length, y.length);
		return a;
	}
	
	
	private int[] createRevertedAnimation(int[] a)
	{
		int[] b = new int[a.length];
		for (int j=0; j<a.length; j+=FRAMESPERSTEP)		
		{	for (int i=0; i<FRAMESPERSTEP; i++)
			{	int i2 = (i==0) ? 0 : FRAMESPERSTEP-i; 	
				b[j+i] = a[j+i2];		
			}
		}
		return b;	
	}
		
	private int[] createRotatedAnimation(int[] a, int degree)
	{
		int[] b = new int[a.length];
		for (int i=0; i<a.length; i++)
		{	int t = a[i] & 0xffff;
			int s = (a[i]>>16)%60;
			int r = (a[i]>>16)/60;
			r = (r + degree + 3600) % 360;
			b[i] = t | ((s+r*60)<<16);		
		}
		return b;	
	}
	
	private int[] createRotatingAnimation(int[] a, int start, int end)
	{
		int[] b = new int[a.length];
		for (int i=0; i<a.length; i++)
		{	int t = a[i] & 0xffff;
			int s = ((a[i]>>16)&0xffff) % 60;
			int r = ((a[i]>>16)&0xffff) / 60;
			
			int frames_until_endposition = i%FRAMESPERSTEP;
			int degree = end - (frames_until_endposition*(end-start))/FRAMESPERSTEP;
			r = (r + degree + 3600) % 360;
			b[i] = t | ((s+r*60)<<16);		
		}
		return b;	
	}
	
	
	
	// --------------------------------- tile rendering -----------------

	public void startDrawing(int viewportwidth, int viewportheight, int screentilesize)
	{	
		startDrawing(viewportwidth,viewportheight, screentilesize, 0,0,0,0);
	}
	
	public void startDrawing(int viewportwidth, int viewportheight, int screentilesize, int offx0, int offy0, int offx1, int offy1)
	{
		this.screentilesize = screentilesize;
		
   		bufferTile.clear();
	
        // average position
//        int offx = (offx0+offx1)/2;
//    	int offy = (offy0+offy1)/2;
       
//        // when the offsets are close enough to each other, only one screen needs to be drawn using the average position 
//        int splitthreasholdx = viewportwidth/4;
//        int splitthreasholdy = viewportheight/4;  
		// when having same offsets, only one draw is necessary      
        if (offx0==offx1 && offy0==offy1)
        {  	Matrix.setIdentityM(matrix,0);     
			Matrix.translateM(matrix,0, -1.0f,1.0f, 0);		
			Matrix.scaleM(matrix,0, 2.0f/viewportwidth, -2.0f/viewportheight, 1.0f);	        
        	Matrix.translateM(matrix,0, offx0, offy0, 0);
        	havematrix2 = false;
        }
        // must draw 2 screens with a terminator line so there is a piece of each players area visible
        else                		
        {	
//        	// adjust offsets so both views are moved closer together
//        	if (offx0>offx+splitthreasholdx)  		offx0 -= splitthreasholdx;
//        	else if (offx0<offx-splitthreasholdx)   offx0 += splitthreasholdx;
//        	else offx0 = offx;
//        	if (offy0>offy+splitthreasholdy)  		offy0 -= splitthreasholdy;
//        	else if (offy0<offy-splitthreasholdy)   offy0 += splitthreasholdy;
//        	else offy0 = offy;
//        	if (offx1>offx+splitthreasholdx)  		offx1 -= splitthreasholdx;
//        	else if (offx1<offx-splitthreasholdx)   offx1 += splitthreasholdx;
//        	else offx1 = offx;
//        	if (offy1>offy+splitthreasholdy)  		offy1 -= splitthreasholdy;
//        	else if (offy1<offy-splitthreasholdy)   offy1 += splitthreasholdy;
//        	else offy1 = offy;
        	
        	// calculate the normal vector (2d) of the delimiter line  (clockwise, 0=right)
//        	float screenratio = (viewportwidth*1.0f) / viewportheight;
			float screendiagonal = (float) Math.sqrt(viewportwidth*viewportwidth+viewportheight*viewportheight);
        	float angle = calcAngle(offx0-offx1, offy0-offy1); 
//System.out.println("angle "+(offx0-offx1)+","+(offy0-offy1)+" -> "+angle);

        	// start with normal matrix for first player
    		Matrix.setIdentityM(matrix,0);
    		// make the matrix tilt to let half of the screen be nearer than 0.0 (which will become invisible because of the near-plane clipping)
    		matrix[2] = -(float)Math.cos(angle)/(screendiagonal/viewportwidth); 
    		matrix[6] = -(float)Math.sin(angle)/(screendiagonal/viewportheight);
	    		
    		// transform to a coordinate system with the units as pixels, with 0,0 at top left corner and the z=0 goes to the near plane
    		Matrix.translateM(matrix,0, -1.0f,1.0f, -1.0f - (2.0f/screendiagonal));		
    		Matrix.scaleM(matrix,0, 2.0f/viewportwidth, -2.0f/viewportheight, 1.0f);
    		
    		// move to desired view position 
        	Matrix.translateM(matrix,0,offx0,offy0, 0.0f);        	        	

        	// start with normal matrix for second player
    		Matrix.setIdentityM(matrix2,0);
    		// make the matrix tilt to let half of the screen be nearer than 0.0 (which will become invisible because of the near-plane clipping)
    		matrix2[2] = (float)Math.cos(angle)/(screendiagonal/viewportwidth); 
    		matrix2[6] = (float)Math.sin(angle)/(screendiagonal/viewportheight); 
	    		
    		// transform to a coordinate system with the units as pixels, with 0,0 at top left corner and the z=0 goes to the near plane
    		Matrix.translateM(matrix2,0, -1.0f,1.0f, -1.0f - (2.0f/screendiagonal));		
    		Matrix.scaleM(matrix2,0, 2.0f/viewportwidth, -2.0f/viewportheight, -1.0f);
    		
    		// move to desired view position 
        	Matrix.translateM(matrix2,0,offx1,offy1, 0.0f);        	        	

			havematrix2 = true;
        }
	}
	
	private float calcAngle(int dx, int dy)
	{
		if (dx==0 && dy==0)
		{	System.out.println("Can not calculate angle of 0/0 coordinates");
			return 0;
		}	
		if (dx>0)
		{	return (float) -Math.atan(((float)dy)/((float)dx));
		}		
		else if (dx<0)
		{	return (float) (Math.PI + Math.atan(((float)dy)/((float)-dx)));
		}
		else 
		{	return dy<0 ? (float) (Math.PI/2) : (float) (-Math.PI/2);
		}		
	}
	
    
    public void addTileToBuffer(int x, int y, int tile)
    {
		if (!bufferTile.hasRemaining())
		{	flush();
		}		
    	
		bufferTile.put((short)x);
		bufferTile.put((short)y);
		bufferTile.put((short)(tile&0x7fff));  
		bufferTile.put((short)((tile>>16)&0x7fff));  
    }
    
    public void addSimplePieceToBuffer(int x, int y, byte piece)
    {		
    	int[] anim = piece2tile[piece&0xff];
    	if (anim!=null)
    	{	
    		addTileToBuffer(x,y,anim[0]);
    		if (anim.length > FRAMESPERSTEP)
    		{	addTileToBuffer(x,y, anim[FRAMESPERSTEP]);
    		}
    	}
    }
    

	public void flush()    
	{
//profiler_draw.done(10);	
	   	// check how many tiles are currently visible  
    	int activetiles = bufferTile.position() / 4;
    	if (activetiles==0)
    	{	return;
    	}
    	
    	// transfer tile info buffer into opengl (consists of 4 parts) 
		glBindBuffer(GL_ARRAY_BUFFER, vboTile);
		for (int i=0; i<4; i++)		
		{	bufferTile.limit(4*activetiles);
    		bufferTile.position(0);
    		glBufferSubData(GL_ARRAY_BUFFER, i*4*MAXTILES*2, 4*activetiles*2, bufferTile);
		}	
    	    
        // clear dynamic buffer for future use
        bufferTile.clear();        
//profiler_draw.done(11);	
    	
    	// set up gl for painting all quads
    	glUseProgram(program);
		
    	// set texture unit 0 to use the texture and tell shader to use texture unit 0
    	glActiveTexture(GL_TEXTURE0);
    	glBindTexture(GL_TEXTURE_2D, txTexture);
    	glUniform1i(uTexture, 0);
    	glUniform1i(uScreenTileSize, screentilesize);
        
    	// enable all vertex attribute arrays and set pointers
    	glBindBuffer(GL_ARRAY_BUFFER, vboCorner);
        glEnableVertexAttribArray(aCorner);
        glVertexAttribPointer(aCorner, 2, GL_UNSIGNED_BYTE, false, 0, 0);

    	glBindBuffer(GL_ARRAY_BUFFER, vboTile);
        glEnableVertexAttribArray(aTile);
        glVertexAttribPointer(aTile, 4, GL_SHORT, false, 0, 0);
               
        // set index array
	    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboIndex);
//profiler_draw.done(12);	
		
		// draw the scene for one player
        glUniformMatrix4fv(uMVPMatrix, 1, false, matrix, 0);
//profiler_draw.done(13);	
	    glDrawElements(GL_TRIANGLES, activetiles*6, GL_UNSIGNED_SHORT, 0);
//profiler_draw.done(14);	

		// optionally draw the scene for the second player also
		if (havematrix2)
        {	glUniformMatrix4fv(uMVPMatrix, 1, false, matrix2, 0);
	    	glDrawElements(GL_TRIANGLES, activetiles*6, GL_UNSIGNED_SHORT, 0);
        }
            
        // Disable vertex arrays
        glDisableVertexAttribArray(aCorner);
        glDisableVertexAttribArray(aTile);
//profiler_draw.done(15);	
        
	}
    

    
}
