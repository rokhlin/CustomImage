package com.selfapps.rav.customimage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import android.widget.Toast;


/**
 * Custom view with Infinite scrolling
 *
 *
 * ***************** Working options:
 * 1. Grid drawing.
 * 2. Scaling
 * 3. Changing values onScroll event
 * 4. OnLongPress Show current Coordinate X / Y
 *
 * ***************** Partially working:
 * 1. Scrolling animation process
 *
 * ***************** Don't working:
 * 1. Scaling MIN / MAX borders
 * 2. Rotating
 *
 *
 */

public class ZoomImage extends ImageView {

    //Log preferences
    private static final boolean logging = false;
    private static final String TAG = "ZoomImage";

    //Touch event types
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    static final int CLICK = 3;

    //current touch event type
    private int mode = NONE;

    //scalling preferences
    private float minScale = 0.5f;
    private float maxScale = 5f;
    private float mScale = 1f;

    //current position of the first visible cell
    private int xCoordinate = 0;
    private int yCoordinate = 0;


    //grid preferences
    private float coordDistance = 20f;
    private float coordMinDistance = 5f;
    private float coordMaxDistance = 100f;

    //current offset on scaling process
    private float currentOffsetX;
    private float currentOffsetY;

    //number of the visible lines by x and y coordinate
    private float[] verticalLines;
    private float[] horizontalLines;

    //Matrix transformation values
    private Matrix mMatrix = new Matrix();
    private float[] mCriticPoints;

    //Changing values of the original bitmap
    private float mRight;
    private float mBottom;
    private float mOriginalBitmapWidth;
    private float mOriginalBitmapHeight;

//    //Rotation angle values
//    private float newRot = 0f;
//    private float d = 0f;
//    private float rotationAngle = 0f;

    //Pointer values that we got on motion event
    private PointF mLastTouch = new PointF();
    private PointF mStartTouch = new PointF();

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector gestureDetector;
    private MotionEvent mEvent;
    private Picture picture;
    private DisplayMetrics display = this.getResources().getDisplayMetrics();



    //Custom view initialization constructors
    public ZoomImage(Context context) {
        super(context);
        init(context);
    }

    public ZoomImage(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomImage(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    /**
     * Starting Initialization of the object;
     * @param context
     */
    private void init(Context context) {
        super.setClickable(true);
        mScaleDetector = new ScaleGestureDetector(context, new ZoomImage.ScaleListener());
        gestureDetector = new GestureDetector(context,new ZoomImage.ScrollListener());

        mCriticPoints = new float[9];
        setImageMatrix(mMatrix);
        setScaleType(ScaleType.MATRIX);
        mMatrix.setScale(1f, 1f);
        invalidateGrid(1,1,mScale);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int bmHeight = getBmHeight();
        int bmWidth = getBmWidth();

        float width = getMeasuredWidth();
        float height = getMeasuredHeight();
        float scale = 1;

        if(logging) Log.d(TAG, "Measured width="+width+", height="+height);
        // If image is bigger then display fit it to screen.
        if (width < bmWidth || height < bmHeight) {
            scale = width > height ? height / bmHeight : width / bmWidth;
        }

        mMatrix.setScale(scale, scale);
        mScale = 1f;

        mOriginalBitmapWidth = scale * bmWidth;
        mOriginalBitmapHeight = scale * bmHeight;

        // Center the image
        float redundantYSpace = (height - mOriginalBitmapHeight);
        float redundantXSpace = (width - mOriginalBitmapWidth);

        mMatrix.postTranslate(redundantXSpace / 2, redundantYSpace / 2);

        setImageMatrix(mMatrix);

    }

    public boolean onTouchEvent(MotionEvent event) {
        mEvent = event;

        mScaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        mMatrix.getValues(mCriticPoints);
        float translateX = mCriticPoints[Matrix.MTRANS_X];
        float translateY = mCriticPoints[Matrix.MTRANS_Y];

        PointF currentPoint = new PointF(event.getX(), event.getY());

        switch (event.getAction()) {
            //when one finger is touching
            //set the mode to DRAG
            case MotionEvent.ACTION_DOWN:
                mLastTouch.set(event.getX(), event.getY());
                mStartTouch.set(mLastTouch);
                mode = DRAG;
                break;

            //when two fingers are touching
            //set the mode to ZOOM
            case MotionEvent.ACTION_POINTER_DOWN:
                mLastTouch.set(event.getX(), event.getY());
                mStartTouch.set(mLastTouch);
                mode = ZOOM;
                break;

            //when a finger moves
            //If mode is applicable move image
            case MotionEvent.ACTION_MOVE:
                if (mode == ZOOM || (mode == DRAG && mScale > minScale)) {
                    // region . Move  image.
                    float deltaX = currentPoint.x - mLastTouch.x;// x difference
                    float deltaY = currentPoint.y - mLastTouch.y;// y difference
                    float scaleWidth = Math.round(mOriginalBitmapWidth * mScale);// width after applying current scale
                    float scaleHeight = Math.round(mOriginalBitmapHeight * mScale);// height after applying current scale

                    //Call OnScale

                    // Move image to lef or right if its width is bigger than display width
                    if (scaleWidth > getWidth()) {
                        if (translateX + deltaX > 0) {
                            deltaX = -translateX;
                        } else if (translateX + deltaX < -mRight) {
                            deltaX = -(translateX + mRight);
                        }
                    } else {
                        deltaX = 0;
                    }

                    // Move image to up or bottom if its height is bigger than display height
                    if (scaleHeight > getHeight()) {
                        if (translateY + deltaY > 0) {
                            deltaY = -translateY;
                        } else if (translateY + deltaY < - mBottom) {
                            deltaY = -(translateY + mBottom);
                        }
                    } else {
                        deltaY = 0;

                    }

                    //move the image with the matrix
                    mMatrix.postScale(deltaX, deltaY,currentPoint.x, currentPoint.y);
                    //set the last touch location to the current
                    mLastTouch.set(currentPoint.x, currentPoint.y);

                }

                break;
            //first finger is lifted
            case MotionEvent.ACTION_UP:
                mode = NONE;
                int xDiff = (int) Math.abs(currentPoint.x - mStartTouch.x);
                int yDiff = (int) Math.abs(currentPoint.y - mStartTouch.y);
                if (xDiff < CLICK && yDiff < CLICK)
                    performClick();
                break;
            // second finger is lifted
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
        }
        setImageMatrix(mMatrix);
        invalidate();
        return true;
    }

    /**
     * Getting bitmap values: WIDTH
     * @return If Drawable resource defined returned intrinsic Width if not 0
     */
    private int getBmWidth() {
        Drawable drawable = getDrawable();
        if (drawable != null) {
            return drawable.getIntrinsicWidth();
        }
        return 0;
    }

    /**
     * Getting bitmap values: HEIGHT
     * @return If Drawable resource defined returned intrinsic Height if not 0
     */
    private int getBmHeight() {
        Drawable drawable = getDrawable();
        if (drawable != null) {
            return drawable.getIntrinsicHeight();
        }
        return 0;
    }



    /**
     * Drawing grid by current values
     * @param dx - delta by X coordinate. Calls on horizontal scroll
     * @param dy - delta by Y coordinate. Calls on vertical scroll
     * @param dScale - Scale factor value. It will recalculate grid in depend of current value
     */
    private void invalidateGrid(float dx, float dy, float dScale){
        if(logging) Log.d(TAG, "dScale="+dScale);
        if(logging) Log.d(TAG, "dx= "+dx+", dy= "+dy);

        coordDistance *= dScale;
        if(logging) Log.d(TAG, "coordDistance= " + coordDistance +", dScale " + dScale );
        //checking drag
        currentOffsetX += dx;
        currentOffsetY += dy;
        if(logging) Log.d(TAG, "currentOffsetX= " + currentOffsetX +", currentOffsetY= " + currentOffsetY );

        //Getting current position of x coordinate
        if(Math.abs(currentOffsetX) > coordDistance){
            xCoordinate += currentOffsetX / coordDistance;
            currentOffsetX = currentOffsetX % coordDistance;
        }

        //Getting current position of y coordinate
        if(Math.abs(currentOffsetY) > coordDistance){
            yCoordinate += currentOffsetY/coordDistance;
            currentOffsetY = currentOffsetY % coordDistance;
        }

        //Get display parameters after scaling
        int width = (int) (display.widthPixels * dScale);
        int height = (int) (display.heightPixels * dScale);

        calculateGridPoints(width,height);

        drawPicture();


    }

    private void drawPicture() {
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                recordPictureByPoints();
            }
        });
        th.start();
    }

    /**
     * Optimizing speed of drawing for the scene rebuilding
     */
    private void recordPictureByPoints() {
        int width = display.widthPixels;
        int height = display.heightPixels;

        picture = new Picture();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.GRAY);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);

        Canvas canvas = picture.beginRecording(width, height);
        Path path = new Path();

        //Calculating and draw path of vertical lines
        for (int i = 0; i < verticalLines.length ; i += 4) {
            path.reset();
            path.moveTo(verticalLines[i], verticalLines[i+1]);
            path.lineTo(verticalLines[i+2], verticalLines[i+3]);
            canvas.drawPath(path,p);
        }

        //Calculating and draw path of horizontal lines
        for (int i = 0; i < horizontalLines.length ; i += 4) {
            path.reset();
            path.moveTo(horizontalLines[i], horizontalLines[i+1]);
            path.lineTo(horizontalLines[i+2], horizontalLines[i+3]);
            canvas.drawPath(path,p);
        }

        // Call this in end of the function
        picture.endRecording();
    }

    /**
     *  Recalculating Grid coordinates on Motion events
     * @param width
     * @param height
     */
    private void calculateGridPoints(int width, int height) {
        int linesCountByWidth = (int) (width / coordDistance);
        int linesCountByHeight = (int) (height / coordDistance);

        if(logging) Log.d(TAG, "lineCountByWidth="+linesCountByWidth+", height="+linesCountByHeight);

        verticalLines = new float[linesCountByHeight*4];
        horizontalLines = new float[linesCountByWidth*4];

        //Calculating points by vertical
        int x = 0;
        while(x < linesCountByHeight*4){
            float pX = (width - currentOffsetX) - (x * coordDistance);
            float pY1 = 0;
            float pY2 = height;
            verticalLines[x] = pX;
            verticalLines[x+1] = pY1;
            verticalLines[x+2] = pX;
            verticalLines[x+3] = pY2;
            x += 4;
        }

        //Calculating points by horizontal
        int y = 0;
        while(y < linesCountByWidth*4){
            float pX = (height - currentOffsetY) - (y * coordDistance);
            float pY1 = 0;
            float pY2 = width;
            horizontalLines[y] = pY1;
            horizontalLines[y+1] = pX;
            horizontalLines[y+2] = pY2;
            horizontalLines[y+3] = pX;
            y += 4;
        }
    }

    /**
     * Drawing  the picture on the screen
     * @param canvas
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawARGB(80, 102, 204, 255);
        canvas.drawPicture(picture);
    }



    /**
     * Implementation of OnScaleGestureListener invalidate Grid on scale event
     * onScale -  will show scale process
     */
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newScale = mScale * scaleFactor;
            if (newScale < maxScale && newScale > minScale) {
                invalidateGrid(1,1,newScale);
            }
            return true;
        }
    }

    /**
     * Implementation of OnGestureScrollListener invalidate Grid on scroll event and onLongPress
     * onLongPress - will show current XY coordinate in depend previous scroll manipulations
     * onScroll -  will show scroll process and will recalulated current coordinates
     */
    private class ScrollListener implements GestureDetector.OnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if(mode != ZOOM && (distanceX > 5 || distanceY > 5 ) ){
                invalidateGrid(distanceX, distanceY, 1);
            }


            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            int xCurrentPushed = (int)(e.getX()/coordDistance) + xCoordinate;
            int yCurrentPushed = (int)(e.getY()/coordDistance) + yCoordinate;
            Toast.makeText(getContext(),"Current position x= " + xCurrentPushed + ", y= " + yCurrentPushed, Toast.LENGTH_LONG).show();
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return false;
        }
    }
}