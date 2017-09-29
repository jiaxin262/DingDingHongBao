package com.android.jiaxin.dingdinghongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class DdHbService extends AccessibilityService {
    public static final String TAG = "DdHbService";
    
    private static final String DD_LOOK_OTHERS_LUCK = "看看大家的手气";
    private static final String DD_HAND_SLOWLY = "手慢了，抢完了";
    private static final String DD_EXPIRES = "已超过24小时";
    private static final String DD_VIEW_SELF_CH = "查看红包";
    private static final String DD_VIEW_OTHERS_CH = "领取红包";
    private static final String DD_HONGBAO_PICK_ACTIVITY = "FestivalRedPacketsPickActivity";
    private static final String DD_HONGBAO_DETAIL_ACTIVITY = "RedPacketsDetailActivity";
    private static final String DD_GENERAL_ACTIVITY = "biz.SplashActivity";
    private String mCurrentActivityName = DD_GENERAL_ACTIVITY;

    private AccessibilityNodeInfo rootNodeInfo;
    private AccessibilityNodeInfo mReceiveNode;
    private AccessibilityNodeInfo mUnpackNode;
    private boolean mLuckyMoneyPicked;
    private boolean mLuckyMoneyReceived;
    private boolean mMutex = false;
    private boolean mChatMutex = false;

    private int mUnpackCount = 0;


    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d(TAG, "onAccessibilityEvent");
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "onAccessibilityEvent type:TYPE_WINDOW_STATE_CHANGED");
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            Log.d(TAG, "onAccessibilityEvent type:TYPE_WINDOW_CONTENT_CHANGED");
        }

        setCurrentActivityName(event);
        if (!mChatMutex) {
            mChatMutex = true;
            watchChat(event);
            mChatMutex = false;
        }
    }

    private void setCurrentActivityName(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        try {
            ComponentName componentName = new ComponentName(event.getPackageName().toString(), event.getClassName().toString());
            getPackageManager().getActivityInfo(componentName, 0);
            mCurrentActivityName = componentName.flattenToShortString();
            Log.d(TAG, "mCurrentActivityName:" + mCurrentActivityName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "mCurrentActivityName NameNotFoundException");
            mCurrentActivityName = DD_GENERAL_ACTIVITY;
        }
    }

    private void watchChat(AccessibilityEvent event) {
        this.rootNodeInfo = getRootInActiveWindow();
        if (rootNodeInfo == null) {
            return;
        }
        mReceiveNode = null;
        mUnpackNode = null;

        checkNodeInfo(event.getEventType());

        /* 如果已经接收到红包并且还没有戳开 */
        if (mLuckyMoneyReceived && !mLuckyMoneyPicked && (mReceiveNode != null)) {
            mMutex = true;
            mReceiveNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            mLuckyMoneyReceived = false;
            mLuckyMoneyPicked = true;
        }
        /* 如果戳开但还未领取 */
        if (mUnpackCount == 1 && (mUnpackNode != null)) {
            int delayFlag = 700;
            new android.os.Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            try {
                                openPacket();
                            } catch (Exception e) {
                                mMutex = false;
                                mLuckyMoneyPicked = false;
                                mUnpackCount = 0;
                            }
                        }
                    },
                    delayFlag);
        }
    }

    private void checkNodeInfo(int eventType) {
        if (this.rootNodeInfo == null) {
            return;
        }
//        if (signature.commentString != null) {
//            sendComment();
//            signature.commentString = null;
//        }

        /* 聊天会话窗口，遍历节点匹配“领取红包”和"查看红包" */
        AccessibilityNodeInfo node1 = this.getTheLastNode(DD_VIEW_OTHERS_CH, DD_VIEW_SELF_CH);
        if (node1 != null && mCurrentActivityName.contains(DD_GENERAL_ACTIVITY)) {
//            if (this.signature.generateSignature(node1, excludeWords)) {
                mLuckyMoneyReceived = true;
                mReceiveNode = node1;
//                Log.d("sig", this.signature.toString());
//            }
            return;
        }

        /* 戳开红包，红包还没抢完，遍历节点匹配“拆红包” */
        AccessibilityNodeInfo node2 = findOpenButton(this.rootNodeInfo);
        if (node2 != null && "android.widget.Button".equals(node2.getClassName()) 
                && mCurrentActivityName.contains(DD_HONGBAO_PICK_ACTIVITY)) {
            mUnpackNode = node2;
            mUnpackCount += 1;
            return;
        }

        /* 戳开红包，红包已被抢完，遍历节点匹配“红包详情”和“手慢了” */
        boolean hasNodes = this.hasOneOfThoseNodes(DD_HAND_SLOWLY, DD_LOOK_OTHERS_LUCK, DD_EXPIRES);
        if (mMutex && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && hasNodes
                && (mCurrentActivityName.contains(DD_HONGBAO_DETAIL_ACTIVITY)
                || mCurrentActivityName.contains(DD_HONGBAO_PICK_ACTIVITY))) {
            mMutex = false;
            mLuckyMoneyPicked = false;
            mUnpackCount = 0;
            performGlobalAction(GLOBAL_ACTION_BACK);
//            signature.commentString = generateCommentString();
        }
    }

    private AccessibilityNodeInfo getTheLastNode(String... texts) {
        int bottom = 0;
        AccessibilityNodeInfo lastNode = null, tempNode;
        List<AccessibilityNodeInfo> nodes;

        for (String text : texts) {
            if (text == null) {
                continue;
            }
            nodes = this.rootNodeInfo.findAccessibilityNodeInfosByText(text);

            if (nodes != null && !nodes.isEmpty()) {
                tempNode = nodes.get(nodes.size() - 1);
                if (tempNode == null) {
                    return null;
                }
                Rect bounds = new Rect();
                tempNode.getBoundsInScreen(bounds);
                if (bounds.bottom > bottom) {
                    bottom = bounds.bottom;
                    lastNode = tempNode;
//                    signature.others = text.equals(WECHAT_VIEW_OTHERS_CH);
                }
            }
        }
        return lastNode;
    }

    private AccessibilityNodeInfo findOpenButton(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        //非layout元素
        if (node.getChildCount() == 0) {
            if ("android.widget.Button".equals(node.getClassName())) {
                return node;
            } else {
                return null;
            }
        }
        //layout元素，遍历找button
        AccessibilityNodeInfo button;
        for (int i = 0; i < node.getChildCount(); i++) {
            button = findOpenButton(node.getChild(i));
            if (button != null)
                return button;
        }
        return null;
    }

    private boolean hasOneOfThoseNodes(String... texts) {
        List<AccessibilityNodeInfo> nodes;
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            nodes = this.rootNodeInfo.findAccessibilityNodeInfosByText(text);
            if (nodes != null && !nodes.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void openPacket() {
        mUnpackNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//
//        DisplayMetrics metrics = getResources().getDisplayMetrics();
//        float dpi = metrics.density;
//        if (android.os.Build.VERSION.SDK_INT <= 23) {
//            mUnpackNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//        } else {
//            if (android.os.Build.VERSION.SDK_INT > 23) {
//
//                Path path = new Path();
//                if (640 == dpi) {
//                    path.moveTo(720, 1575);
//                } else {
//                    path.moveTo(540, 1060);
//                }
//                GestureDescription.Builder builder = new GestureDescription.Builder();
//                GestureDescription gestureDescription = builder.addStroke(new GestureDescription.StrokeDescription(path, 450, 50)).build();
//                dispatchGesture(gestureDescription, new GestureResultCallback() {
//                    @Override
//                    public void onCompleted(GestureDescription gestureDescription) {
//                        Log.d("test", "onCompleted");
//                        mMutex = false;
//                        super.onCompleted(gestureDescription);
//                    }
//
//                    @Override
//                    public void onCancelled(GestureDescription gestureDescription) {
//                        Log.d("test", "onCancelled");
//                        mMutex = false;
//                        super.onCancelled(gestureDescription);
//                    }
//                }, null);
//
//            }
//        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "onServiceConnected");
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "onInterrupt");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

}
