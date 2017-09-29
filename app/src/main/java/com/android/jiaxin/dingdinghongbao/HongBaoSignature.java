package com.android.jiaxin.dingdinghongbao;

import android.graphics.Rect;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

public class HongBaoSignature {
    public static final String TAG = "HongBaoSignature";

    public String sender;
    public String content;
    public String time;
    public String contentDescription = "";

    public boolean generateSignature(AccessibilityNodeInfo node) {
        try {
            /* The hongbao container node. It should be a FrameLayout. By specifying that, we can avoid text messages. */
            AccessibilityNodeInfo hongbaoNode = node.getParent();
            if (!"android.widget.FrameLayout".equals(hongbaoNode.getClassName())) {
                Log.d(TAG, "---return---container is not FrameLayout");
                return false;
            }

            /* The text in the hongbao. Should mean something. */
            String hongbaoContent = hongbaoNode.getChild(0).getText().toString();
            String hongbaoDesc = hongbaoNode.getChild(hongbaoNode.getChildCount()-1).getText().toString();
            if (!TextUtils.isEmpty(hongbaoDesc) && hongbaoDesc.contains("已领取")) {
                Log.d(TAG, "---return---已领取");
                return false;
            }

            /* Check the user's exclude words list. */
//            String[] excludeWordsArray = excludeWords.split(" +");
//            for (String word : excludeWordsArray) {
//                if (word.length() > 0 && hongbaoContent.contains(word)) return false;
//            }

            /* The container node for a piece of message. It should be inside the screen.
                Or sometimes it will get opened twice while scrolling. */
            AccessibilityNodeInfo messageNode = hongbaoNode.getParent();

            Rect bounds = new Rect();
            messageNode.getBoundsInScreen(bounds);
            Log.d(TAG, "top=" + bounds.top);
            if (bounds.top < 0) {
                Log.d(TAG, "---return---message is out of screen");
                return false;
            }

            /* The sender should mean something too. */
            String[] hongbaoInfo = getHongBaoInfoFromNode(messageNode);

            if (this.getSignature(hongbaoInfo[0], hongbaoContent, hongbaoInfo[1]).equals(this.toString())) {
                Log.d(TAG, "---return---is same with last one");
                return false;
            }

            /* So far we make sure it's a valid new coming hongbao. */
            this.sender = hongbaoInfo[0];
            this.time = hongbaoInfo[1];
            this.content = hongbaoContent;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String toString() {
        return this.getSignature(this.sender, this.content, this.time);
    }

    private String getSignature(String... strings) {
        String signature = "";
        for (String str : strings) {
            if (str == null) return null;
            signature += str + "|";
        }

        Log.d(TAG, "signature:" + signature.substring(0, signature.length() - 1));

        return signature.substring(0, signature.length() - 1);
    }

    public String getContentDescription() {
        return this.contentDescription;
    }

    public void setContentDescription(String description) {
        this.contentDescription = description;
    }

    private String[] getHongBaoInfoFromNode(AccessibilityNodeInfo node) {
        String[] result = {"unknownSender", "unknownTime"};
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo nodeChild = node.getChild(i);
            if ("android.widget.TextView".equals(nodeChild.getClassName())) {
                CharSequence nodeText = nodeChild.getText();
                if (!TextUtils.isEmpty(nodeText)) {
                    if ("unknownSender".equals(result[0])) {
                        result[0] = nodeText.toString();
                    } else if ("unknownTime".equals(result[1])) {
                        result[1] = nodeText.toString();
                    }
                }
            }
        }
        return result;
    }

    public void cleanSignature() {
        this.content = "";
        this.sender = "";
        this.time = "";
    }
}
