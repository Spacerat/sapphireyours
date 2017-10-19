
// object allocator function
var GfxRenderer = function()
{   Renderer.call(this);
    this.TITLEPICTURE = null;
    this.FINISHEDMARKER_VISITED = null;
    this.FINISHEDMARKER_SOLVED = null;
    this.FINISHEDMARKER_PERFECT = null;
    
    this.program = 0;
    this.uMVPMatrix = 0;
    this.uTexture = 0;
    this.uTextureSize = 0;
    this.aCorner = 0;
    this.aTextureCoordinates = 0;
        
    this.iboIndex = 0;              // buffer holding short
    this.vboCorner = 0;             // buffer holding float[2] - destination coordinates (in pixel)
    this.vboTextureCoordinates = 0; // buffer holding short[2] - font texture coordinates (in pixel)    
    this.txTexture = 0;             // texture buffer
    
    // client-side buffers to prepare the data before moving it into their gl counterparts
    this.bufferCorner = null;
    this.bufferTextureCoordinates = null;
    this.bufferFill = 0;  // how many glyphs are in the buffer
            
    this.matrix = null;       // projection matrix    
};
GfxRenderer.prototype = Object.create(Renderer.prototype);
    
GfxRenderer.ATLASWIDTH = 512;
GfxRenderer.ATLASHEIGHT = 512;
    
GfxRenderer.vertexShaderCode =
            "uniform mat4 uMVPMatrix;      "+
            "uniform vec2 uTextureSize;    "+   // width/height of texture in pixels
            "attribute vec2 aCorner;       "+   // location on screen (before transformation)
            "attribute vec2 aTextureCoordinates;  "+   // coordinate in texture (in pixels)   
            "varying vec2 vTextureCoordinates;    "+   // coordinate in texture (in 0.0 - 1.0) to be passed to fragment shader
            "void main() {                        "+
            "  vec4 p;                            "+
            "  p[0] = aCorner[0];                 "+
            "  p[1] = aCorner[1];                 "+
            "  p[2] = 0.0;                        "+
            "  p[3] = 1.0;                        "+
            "  gl_Position = uMVPMatrix * p;      "+
            "  vTextureCoordinates[0] = aTextureCoordinates[0]/uTextureSize[0]; "+
            "  vTextureCoordinates[1] = aTextureCoordinates[1]/uTextureSize[1]; "+
            "}                                         "+
            "";    
GfxRenderer.fragmentShaderCode =
            "uniform sampler2D uTexture;                      "+  // uniform specifying the texture 
            "varying mediump vec2 vTextureCoordinates;        "+  // input from vertex shader
            "void main() {                                    "+
            "   gl_FragColor = texture2D(uTexture,vTextureCoordinates);               "+
            "}                                                                        "+
            "";
    
GfxRenderer.MAXRECTANGLES = 500;  // number of rectangles that can be rendered in one call
   
   
GfxRenderer.prototype.$ = function(gl)
{
    Renderer.prototype.$.call(this,gl);

        // create shaders and link together
    this.program = this.createProgram(GfxRenderer.vertexShaderCode,GfxRenderer.fragmentShaderCode);
    // extract the bindings for the uniforms and attributes
    this.uMVPMatrix = gl.getUniformLocation(this.program, "uMVPMatrix");
    this.uTexture = gl.getUniformLocation(this.program, "uTexture");
    this.uTextureSize = gl.getUniformLocation(this.program, "uTextureSize");
    this.aCorner = gl.getAttribLocation(this.program, "aCorner");
    this.aTextureCoordinates = gl.getAttribLocation(this.program, "aTextureCoordinates");
        
    // create index buffer
    this.iboIndex = gl.createBuffer();
    var sb = new Uint16Array(6*4*GfxRenderer.MAXRECTANGLES);
    for (var i=0; i<GfxRenderer.MAXRECTANGLES; i++)
    {   sb[6*i+0] = 4*i+0; 
        sb[6*i+1] = 4*i+1; 
        sb[6*i+2] = 4*i+2; 
        sb[6*i+3] = 4*i+1; 
        sb[6*i+4] = 4*i+3; 
        sb[6*i+5] = 4*i+2; 
    }
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.iboIndex);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, sb, gl.STATIC_DRAW);
    sb = null;
    
    // create buffers (gl and client) that hold 4 entries for every rectangle
    this.bufferCorner = new Float32Array(2*4*GfxRenderer.MAXRECTANGLES);
    this.vboCorner = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, this.vboCorner);
    gl.bufferData(gl.ARRAY_BUFFER, 2*4*4*GfxRenderer.MAXRECTANGLES, gl.DYNAMIC_DRAW);

    this.bufferTextureCoordinates = new Uint16Array(2*4*GfxRenderer.MAXRECTANGLES);
    this.vboTextureCoordinates = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, this.vboTextureCoordinates);
    gl.bufferData(gl.ARRAY_BUFFER, 2*4*2*GfxRenderer.MAXRECTANGLES, gl.DYNAMIC_DRAW);

    // allocate memory for projection matrix
    this.matrix = new Array(16);

    // allocate memory for texture atlas
    this.txTexture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, this.txTexture);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, GfxRenderer.ATLASWIDTH,GfxRenderer.ATLASHEIGHT, 
        0, gl.RGBA, gl.UNSIGNED_BYTE, null);

    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR); // NEAREST);                  

    // load the graphics files and put into texture atlas
    var freespace = new FreespaceManager().$(GfxRenderer.ATLASWIDTH,GfxRenderer.ATLASHEIGHT);

    var that = this;
    this.loadGfx(freespace, "splash", function(pos_and_dim)
    {   that.TITLEPICTURE = pos_and_dim;
    });
    this.loadGfx(freespace, "CompletionNO", function(pos_and_dim)
    {   that.FINISHEDMARKER_VISITED = pos_and_dim;
    });
    this.loadGfx(freespace, "CompletionFINISHED", function(pos_and_dim)
    {   that.FINISHEDMARKER_SOLVED = pos_and_dim;
    });
    this.loadGfx(freespace, "CompletionPERFECT", function(pos_and_dim)
    {   that.FINISHEDMARKER_PERFECT = pos_and_dim;
    });
    
    return this;    
};

GfxRenderer.prototype.isLoaded = function()
{
    return this.TITLEPICTURE!==null && this.FINISHEDMARKER_VISITE!==null 
        && this.FINISHEDMARKER_SOLVED!==null && this.FINISHEDMARKER_PERFECT!==null;
};

GfxRenderer.prototype.loadGfx = function(freespace, name, callback) 
{
    var that = this;
    
    var image = new Image();
    image.addEventListener
    (   'load', function() 
        {   var gl = that.gl;
        
            var w = image.naturalWidth;
            var h = image.naturalHeight;
            
            var pos_and_dim = freespace.allocateArea(w,h);

            gl.bindTexture(gl.TEXTURE_2D, that.txTexture);
            gl.texSubImage2D(gl.TEXTURE_2D, 0, pos_and_dim[0],pos_and_dim[1], gl.RGBA, gl.UNSIGNED_BYTE, image);
            
            console.log("done loading",name,":",pos_and_dim);
            callback(pos_and_dim);
        }
   );
   image.src = "gfx/" + name + ".png";
};        
    
GfxRenderer.prototype.startDrawing = function(viewportwidth,viewportheight)
{
        // clear buffer fill state
        this.bufferFill = 0;
        
        // transfer coordinate system from the opengl-standard to a pixel system (0,0 is top left)
        Matrix.setIdentityM(this.matrix,0);     
        Matrix.translateM(this.matrix,0, -1.0,1.0, 0);     
        Matrix.scaleM(this.matrix,0, 2.0/viewportwidth, -2.0/viewportheight, 1.0);
};
    
GfxRenderer.prototype.flush = function()
{
        var gl = this.gl;

        var numrectangles = this.bufferFill;
        this.bufferFill = 0;
        if (numrectangles<=0)
        {   return;
        }
        
        // transfer buffers into opengl 
        gl.bindBuffer(gl.ARRAY_BUFFER, this.vboCorner);  
        gl.bufferSubData(gl.ARRAY_BUFFER,0, this.bufferCorner.subarray(0,4*2*numrectangles));

//        console.log(numrectangles,"corners:",this.bufferCorner.subarray(0,4*2*numrectangles));
        
        gl.bindBuffer(gl.ARRAY_BUFFER, this.vboTextureCoordinates);       
        gl.bufferSubData(gl.ARRAY_BUFFER,0, this.bufferTextureCoordinates.subarray(0,4*2*numrectangles));  

        // set up gl for painting all triangles
        gl.useProgram(this.program);
        
        // set texture unit 0 to use the texture and tell shader to use texture unit 0
        gl.activeTexture(gl.TEXTURE0);
        gl.bindTexture(gl.TEXTURE_2D, this.txTexture);
        gl.uniform1i(this.uTexture, 0);
        
        // enable all vertex attribute arrays and set pointers
        gl.enableVertexAttribArray(this.aCorner);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.vboCorner);
        gl.vertexAttribPointer(this.aCorner, 2, gl.FLOAT, false, 0, 0);

        gl.enableVertexAttribArray(this.aTextureCoordinates);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.vboTextureCoordinates);
        gl.vertexAttribPointer(this.aTextureCoordinates, 2, gl.SHORT, false, 0, 0);

        // set uniform data 
        gl.uniformMatrix4fv(this.uMVPMatrix, false, this.matrix);
        gl.uniform2f (this.uTextureSize, GfxRenderer.ATLASWIDTH, GfxRenderer.ATLASHEIGHT);

        // Draw all quads in one big call
        gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.iboIndex);
        gl.drawElements(gl.TRIANGLES,numrectangles*6, gl.UNSIGNED_SHORT, 0);

        // disable arrays
        gl.disableVertexAttribArray(this.aCorner);
        gl.disableVertexAttribArray(this.aTextureCoordinates);
}
    
GfxRenderer.prototype.addGraphic = function(source, x1, y1, width, height)
    {
        if (this.bufferFill>=GfxRenderer.MAXRECTANGLES)
        {   flush();
        }       
        var cursor = this.bufferFill*8;
        
        // target coordinates        
        var x2 = x1+width;
        var y2 = y1+height;
        
        // source coordinates
        var sx1 = source[0];
        var sy1 = source[1];
        var sx2 = sx1 + source[2];
        var sy2 = sy1 + source[3];
            
        // top-left corner
        this.bufferCorner[cursor+0] = x1; 
        this.bufferCorner[cursor+1] = y1;       
        this.bufferTextureCoordinates[cursor+0] = sx1;   
        this.bufferTextureCoordinates[cursor+1] = sy1;
        // top-right corner
        this.bufferCorner[cursor+2] = x2; 
        this.bufferCorner[cursor+3] = y1;       
        this.bufferTextureCoordinates[cursor+2] = sx2;   
        this.bufferTextureCoordinates[cursor+3] = sy1;
        // bottom-left corner
        this.bufferCorner[cursor+4] = x1; 
        this.bufferCorner[cursor+5] = y2;       
        this.bufferTextureCoordinates[cursor+4] = sx1;   
        this.bufferTextureCoordinates[cursor+5] = sy2;
        // bottom-right corner
        this.bufferCorner[cursor+6] = x2; 
        this.bufferCorner[cursor+7] = y2;       
        this.bufferTextureCoordinates[cursor+6] = sx2;   
        this.bufferTextureCoordinates[cursor+7] = sy2;
        
        this.bufferFill++;
};
    
