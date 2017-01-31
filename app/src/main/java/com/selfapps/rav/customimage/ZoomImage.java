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
import android.view.View;
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

public class ZoomImage extends View {

    //Log preferences
    private static final boolean logging = false;
    private static final String TAG = "ZoomImage";

    //Touch event types
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    static final int CLICK = 3;
    static final int ROTATE = 4;

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

    //Screen and Canvas properties
    private int width;
    private int height;
    private int canvasWidth;
    private int canvasHeight;

    //Rotation angle values
    private float angle = 0;

    //Pointer values that we got on motion event
    private PointF currentPoint;
    private PointF mLastTouch = new PointF();
    private PointF mStartTouch = new PointF();

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector gestureDetector;
    private MotionEvent mEvent;
    private Picture picture;
    private DisplayMetrics display = this.getResources().getDisplayMetrics();
    private Paint p;
    private float offsetX;
    private float offsetY;

    private float fY; //first point by Y
    private float fX; //first point by X
    private float sX; //second point by X
    private float sY; //second point by Y


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

        width = display.widthPixels;
        height = display.heightPixels;

        canvasWidth = width * 3;
        canvasHeight = height * 3;

        pictureInit();

        mCriticPoints = new float[9];
        mMatrix.setScale(1f, 1f);
        mMatrix.preTranslate(-width,-height);
        invalidateGrid(1,1);
    }

    private void pictureInit() {
        picture = new Picture();
        p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.GRAY);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public boolean onTouchEvent(MotionEvent event) {
        mEvent = event;

        mScaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        mMatrix.getValues(mCriticPoints);
        float translateX = mCriticPoints[Matrix.MTRANS_X];
        float translateY = mCriticPoints[Matrix.MTRANS_Y];

        if(logging) Log.d(TAG, "translateX="+translateX+", translateY="+translateY);


        currentPoint = new PointF(event.getX(), event.getY());

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
                sX = mStartTouch.x;
                sY = mStartTouch.y;
                fX = mLastTouch.x;
                fY = mLastTouch.y;
                mode = ZOOM;
                break;

            //when a finger moves
            //If mode is applicable move image
            case MotionEvent.ACTION_MOVE:
                //get new points after rotating
                float nsX = mStartTouch.x;
                float nsY = mStartTouch.y;
                float nfX = mLastTouch.x;
                float nfY = mLastTouch.y;

                //get new Rotating angle
                float newAngle = angleBetweenLines(fX,fY,sX,sY,nfX,nfY,nsX,nsY);
                if(newAngle>10f) mode = ROTATE;

                if (mode == ZOOM || (mode == DRAG && mScale > minScale)) {
                    float deltaX = currentPoint.x - mLastTouch.x;// x difference
                    float deltaY = currentPoint.y - mLastTouch.y;// y difference

                    // mMatrix.postScale(deltaX, deltaY, currentPoint.x, currentPoint.y);
                    //set the last touch location to the current
                    mLastTouch.set(currentPoint.x, currentPoint.y);

                }
                if(mode == ROTATE){
                    //clear last points
                    sX = nfX;
                    sY = nsY;
                    fX = nfX;
                    fY = nfY;

                    PointF middlePoint = getMiddlePoint(sX,sY,fX,fY);

                    if(logging) Log.d(TAG, "newAngle="+newAngle+", oldAngle="+angle);

                    if(newAngle > angle + 15f){
                        mMatrix.postRotate(newAngle / 3,middlePoint.x, middlePoint.y );
                        angle = 0;
                    }
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

        invalidate();
        return true;
    }

    /**
     * @param sX coordinate of the second point by X
     * @param sY coordinate of the second point by Y
     * @param fX coordinate of the first point by X
     * @param fY coordinate of the first point by Y
     * @return PointF in the center of getting coordinates for rotation or scaling transformation
     */
    private PointF getMiddlePoint(float sX, float sY, float fX, float fY) {
        float mX = (sX + fX) / 2;
        float mY = (sY + fY) / 2;
        return new PointF(mX,mY);
    }

    /**
     * Angle calculating by changing of points position.
     * @param fX coordinate of the first point by X
     * @param fY coordinate of the first point by Y
     * @param sX coordinate of the second point by X
     * @param sY coordinate of the second point by Y
     *
     * New values of coordinate after touch event:
     * @param nfX new coordinate of the first point by X
     * @param nfY new coordinate of the first point by Y
     * @param nsX new coordinate of the second point by X
     * @param nsY new coordinate of the second point by Y
     * @return Rotation angle
     */
    private float angleBetweenLines (float fX, float fY, float sX, float sY, float nfX, float nfY, float nsX, float nsY) {
        float angle1 = (float) Math.atan2( (fY - sY), (fX - sX) );
        float angle2 = (float) Math.atan2( (nfY - nsY), (nfX - nsX) );

        float angle = ((float)Math.toDegrees(angle1 - angle2)) % 360;
        if (angle < -180.f) angle += 360.0f;
        if (angle > 180.f) angle -= 360.0f;
        return -angle;
    }

    /**
     * Drawing grid by current values
     * @param dx - delta by X coordinate. Calls on horizontal scroll
     * @param dy - delta by Y coordinate. Calls on vertical scroll
     */
    private void invalidateGrid(float dx, float dy){
        calculateGridPoints(dx,dy);
        drawPicture();
    }

    private void drawPicture() {
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                recordPictureByPoints();
                if(logging) Log.d(TAG, Thread.currentThread().getName()+" stopped");
            }
        });
        if(logging) Log.d(TAG, th.getName() +" started");
        th.start();
    }

    /**
     * Optimizing speed of drawing for the scene rebuilding
     */
    private void recordPictureByPoints() {

        Canvas canvas = picture.beginRecording(canvasWidth, canvasHeight);
        Path path = new Path();

        //Calculating and draw path of vertical lines
        for (int i = 0; i < verticalLines.length ; i += 4) {
            path.reset();
            path.moveTo(verticalLines[i], verticalLines[i+1]);
            path.lineTo(verticalLines[i+2], verticalLines[i+3]);
            path.transform(mMatrix);
            //path.offset(offsetX,offsetY);
            canvas.drawPath(path,p);
        }

        //Calculating and draw path of horizontal lines
        for (int i = 0; i < horizontalLines.length ; i += 4) {
            path.reset();
            path.moveTo(horizontalLines[i], horizontalLines[i+1]);
            path.lineTo(horizontalLines[i+2], horizontalLines[i+3]);
            path.transform(mMatrix);
            // path.offset(offsetX,offsetY);
            canvas.drawPath(path,p);

        }

        // Call this in end of the function
        picture.endRecording();
    }




    /**
     *  Recalculating Grid coordinates on Motion events
     * @param translateX
     * @param translateY
     */
    private void calculateGridPoints(float translateX, float translateY) {
        int linesCountByWidth = (int) (canvasWidth / coordDistance);
        int linesCountByHeight = (int) (canvasHeight / coordDistance);


        offsetX = translateX;
        offsetY = translateY;

        if(logging) Log.d(TAG, "lineCountByWidth="+linesCountByWidth+", lineCountByHeight="+linesCountByHeight);

        verticalLines = new float[linesCountByHeight*4];
        horizontalLines = new float[linesCountByWidth*4];

        //Calculating points by vertical
        int x = 0;
        float pY1 = 0 - translateY;
        float pY2 = canvasHeight -translateY;
        while(x < linesCountByWidth){
            float pX = (0 - translateX) +  (x * coordDistance);
            verticalLines[x] = pX;
            verticalLines[x+1] = pY1;
            verticalLines[x+2] = pX;
            verticalLines[x+3] = pY2;
            x += 4;
        }

        //Calculating points by horizontal
        int y = 0;
        pY1 = 0 - translateY;
        pY2 = canvasWidth - translateY;
        while(y < linesCountByHeight){
            float pX = (0 - translateX) + (y * coordDistance);
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
                invalidateGrid(1,1);
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
                invalidateGrid(distanceX, distanceY);
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