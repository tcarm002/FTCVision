package org.lasarobotics.vision.test.opmode.extensions;

import android.util.Log;
import android.widget.Toast;

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

import org.lasarobotics.vision.test.detection.objects.Contour;
import org.lasarobotics.vision.test.image.Drawing;
import org.lasarobotics.vision.test.image.Transform;
import org.lasarobotics.vision.test.opmode.VisionOpMode;
import org.lasarobotics.vision.test.util.color.ColorRGBA;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import org.lasarobotics.vision.test.detection.QRDetector;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;

import java.util.Arrays;

/**
 * Uses ZXing library to detect QR codes
 */
public class QRExtension implements VisionExtension {
    private static final float GRAY_CARD_MULTIPLIER = 1.72f; //Multiplies line size that extends to gray card
    private static final float GRAY_CARD_BOX_SIZE = 0.3f; //Multiplies gray box size
    private QRDetector qrd;
    private Result lastResult;

    private boolean isEnabled = true;
    public void setEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }
    public boolean isEnabled() {
        return isEnabled;
    }

    private boolean stopOnFTCQRCode = true;
    public void setStopOnFTCQRCode(boolean stopOnFTCQRCode) {
        this.stopOnFTCQRCode = stopOnFTCQRCode;
    }
    public boolean doesStopOnFTCQRCode() {
        return stopOnFTCQRCode;
    }

    private boolean shouldColorCorrect = false;
    public void setShouldColorCorrect(boolean shouldColorCorrect) {
        this.shouldColorCorrect = shouldColorCorrect;
    }
    public boolean doesColorCorrect() {
        return shouldColorCorrect;
    }

    private boolean matDebugInfo = false;
    public boolean hasDebugInfo(boolean matDebugInfo) {
        return matDebugInfo;
    }
    public void setDebugInfo(boolean matDebugInfo) {
        this.matDebugInfo = matDebugInfo;
    }

    private boolean hasInit = false;
    public boolean hasInit() {
        return hasInit;
    }

    private String reason;
    public boolean hasErrorReason() {
        return reason != null;
    }
    public String getErrorReason() {
        return reason != null ? reason : "";
    }

    private String text;
    public boolean hasText() {
        return text != null;
    }
    public String getText() {
        return text != null ? text : "";
    }

    public FTCQRCodeInfo getCodeInfo() {
        if(text == null) {
            return new FTCQRCodeInfo(false, -1, -1);
        } else {
            return QRExtension.parseFTCQRCode(text);
        }
    }

    //Sample valid FTCQRCode: !9874_0
    //! is just a character to make sure we don't pick up stray qr codes
    //9874 is team number
    //_ is team number and value separator
    //0 is value
    public static FTCQRCodeInfo parseFTCQRCode(String qrCode) {
        if(qrCode.charAt(0) != '!') {
            return new FTCQRCodeInfo(false, -1, -1);
        }
        int team = 0;
        int i;
        for(i = 1; i < qrCode.length(); i++) {
            char c = qrCode.charAt(i);
            if(c >= '0' && c <= '9') {
                //c is a digit
                int in = c - '0'; //integer representation of c
                team *= 10;
                team += in;
            } else {
                if(c == '_') {
                    //proper separator
                    break;
                } else {
                    return new FTCQRCodeInfo(false, team, -1);
                }
            }
        }
        i++;
        int val = 0;
        for(; i < qrCode.length(); i++) {
            char c = qrCode.charAt(i);
            if(c >= '0' && c <= '9') {
                //c is a digit
                int in = c - '0'; //integer representation of c
                val *= 10;
                val += in;
            } else {
                return new FTCQRCodeInfo(false, team, val);
            }
        }
        return new FTCQRCodeInfo(true, team, val);
    }

    public static class FTCQRCodeInfo {
        private boolean isValid;
        private int team;
        private int num;
        public FTCQRCodeInfo(boolean isValid, int team, int num) {
            this.isValid = isValid;
            this.team = team;
            this.num = num;
        }
        public boolean isValid() {
            return isValid;
        }
        public int getTeam() {
            return team;
        }
        public int getNum() {
            return num;
        }
    }

    @Override
    public void init(VisionOpMode opmode) {
        qrd = new QRDetector();
        hasInit = true;
    }

    public QRDetector.Orientation getOrientation() {
        return lastResult == null ? QRDetector.Orientation.UNKNOWN : QRDetector.getOrientation(lastResult.getResultPoints());
    }

    @Override
    public void loop(VisionOpMode opmode) {
        //do nothing
    }

    @Override
    public Mat frame(VisionOpMode opmode, Mat rgba, Mat gray) {
        try {
            lastResult = qrd.detectFromMat(rgba);
            if(matDebugInfo) {
                ResultPoint[] rp = lastResult.getResultPoints();
                ColorRGBA red = new ColorRGBA("#ff0000");
                ColorRGBA blue = new ColorRGBA("#88ccff");
                ColorRGBA green = new ColorRGBA("#66ff99");
                ColorRGBA white = new ColorRGBA("#ffffff");
                Drawing.drawText(rgba, "0", new Point(rp[0].getX(), rp[0].getY()), 1.0f, blue);
                for(int i = 0; i < rp.length - 1; i++) {
                    ResultPoint topLeft = lastResult.getResultPoints()[i];
                    ResultPoint bottomRight = lastResult.getResultPoints()[i+1];
                    Drawing.drawText(rgba, String.valueOf(i+1), new Point(bottomRight.getX(), bottomRight.getY()), 1.0f, blue);
                    Drawing.drawLine(rgba, new Point(topLeft.getX(), topLeft.getY()), new Point(bottomRight.getX(), bottomRight.getY()), new ColorRGBA("#ff0000"));
                }
                if(rp.length >= 3) {
                    float p0p1avgX = (rp[0].getX() + rp[1].getX())/2;
                    float p0p1avgY = (rp[0].getY() + rp[1].getY())/2;
                    float p1p0yDiff = (rp[1].getY() - rp[0].getY());
                    float p2p1xDiff = (rp[2].getX() - rp[1].getX());
                    float p2p1yDiff = (rp[2].getY() - rp[1].getY());
                    float p2p0yDiff = (rp[2].getY() - rp[0].getY());
                    float p2p0xDiff = (rp[2].getY() - rp[0].getY());
                    float adjMult = 1 + GRAY_CARD_MULTIPLIER;
                    float np1x = p0p1avgX + p2p1xDiff * adjMult;
                    float np1y = p0p1avgY + p2p1yDiff * adjMult;
                    Drawing.drawLine(rgba, new Point(p0p1avgX, p0p1avgY), new Point(np1x, np1y), green);
                    float xOffset = p2p1xDiff * GRAY_CARD_MULTIPLIER;
                    float yOffset = p2p1yDiff * GRAY_CARD_MULTIPLIER;
                    Point[] box = new Point[] {
                            new Point(rp[1].getX() + xOffset, rp[1].getY() + yOffset),
                            new Point(rp[2].getX() + xOffset, rp[2].getY() + yOffset),
                            new Point(rp[0].getX() + xOffset + (rp[2].getX() - rp[1].getX()), rp[2].getY() + yOffset + (rp[0].getY() - rp[1].getY())),
                            new Point(rp[0].getX() + xOffset, rp[0].getY() + yOffset)
                    };
                    Contour c = new Contour(new MatOfPoint(box));
                    Drawing.drawLine(rgba, box[0], box[1], green);
                    Drawing.drawLine(rgba, box[1], box[2], green);
                    Drawing.drawLine(rgba, box[2], box[3], green);
                    Drawing.drawLine(rgba, box[3], box[0], green);

                    Point upperMost = new Point(Float.MAX_VALUE, Float.MAX_VALUE);
                    int upperMostIndex = -1;
                    float centerX = 0;
                    float centerY = 0;
                    for(int i = 0; i < box.length; i++) {
                        Drawing.drawText(rgba, String.valueOf(i), box[i], 1.0f, blue);
                        if(box[i].y < upperMost.y) {
                            upperMost = box[i];
                            upperMostIndex = i;
                        }
                        centerX += box[i].x;
                        centerY += box[i].y;
                    }
                    Point oppUpperMost = box[(upperMostIndex + 2) % box.length]; //Point opposite side of uppermost
                    Drawing.drawLine(rgba, upperMost, oppUpperMost, blue);
                    centerX /= box.length;
                    centerY /= box.length;
                    Drawing.drawRectangle(rgba, new Point(centerX - 1, centerY - 1), new Point(centerX + 1, centerY + 1), red);

                    float lineFromUpperMostToBottomMostLength = (float)Math.sqrt(Math.pow(upperMost.x - oppUpperMost.x, 2) + Math.pow(upperMost.y - oppUpperMost.y, 2));
                    Drawing.drawLine(rgba,
                            new Point((upperMost.x + oppUpperMost.x)/2, (upperMost.y + oppUpperMost.y)/2),
                            new Point(oppUpperMost.x, oppUpperMost.y),
                            white);
                    float line1 = lineFromUpperMostToBottomMostLength/2;
                    float line2 = (float)Math.sqrt(Math.pow(box[0].x - box[1].x, 2) + Math.pow(box[0].y - box[1].y, 2));
                    Drawing.drawLine(rgba, box[0], box[1], white);
                    float angleOfRotation = (float)Math.acos(line2 / line1);
                    float newSquareSideLength = line2/(float)(Math.cos(angleOfRotation) + Math.sin(angleOfRotation));
                    Point[] newBox = new Point[] {
                            new Point(centerX - newSquareSideLength / 2, centerY - newSquareSideLength / 2),
                            new Point(centerX + newSquareSideLength / 2, centerY + newSquareSideLength / 2)
                    };
                    Drawing.drawRectangle(rgba, newBox[0], newBox[1], red);

                    Drawing.drawText(rgba, "Oppmost line len: " + lineFromUpperMostToBottomMostLength, new Point(0, 110), 1.0f, white);
                    Drawing.drawText(rgba, "line1: " + line1 + " line2: " + line2 + " line2/line1: " + (line2/line1), new Point(0, 140), 1.0f, white);
                    Drawing.drawText(rgba, "angle: " + angleOfRotation + " deg: " + Math.toDegrees(angleOfRotation), new Point(0, 170), 1.0f, white);
                    Drawing.drawText(rgba, "new len: " + newSquareSideLength, new Point(0, 200), 1.0f, white);
                    Drawing.drawText(rgba, "new rect: " + Arrays.toString(newBox), new Point(0, 230), 1.0f, white);
                }
            }
            text = lastResult.getText();
            reason = null;
            qrd.reset();
        } catch(NotFoundException ex) {
            text = null;
            reason = "QR Code not found. Extra info: " + ex.getMessage();
        } catch(ChecksumException ex) {
            text = null;
            reason = "QR Code has invalid checksum. Extra info: " + ex.getMessage();
        } catch(FormatException ex) {
            text = null;
            reason = "QR Code not properly formatted. Extra info: " + ex.getMessage();
        }
        return rgba;
    }

    @Override
    public void stop(VisionOpMode opmode) {
        //do nothing
    }
}