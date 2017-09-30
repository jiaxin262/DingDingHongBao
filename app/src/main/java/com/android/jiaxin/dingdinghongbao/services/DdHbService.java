package com.android.jiaxin.dingdinghongbao.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.jiaxin.dingdinghongbao.HongBaoSignature;

import java.util.List;

public class DdHbService extends AccessibilityService {
    public static final String TAG = "DdHbService";

    private static final String DD_LOOK_OTHERS_LUCK = "看看哪些人拿了该红包";
    private static final String DD_HAND_SLOWLY = "手慢了，抢完了";
    private static final String DD_EXPIRES = "已超过24小时";
    private static final String DD_VIEW_SELF_CH = "查看红包";
    private static final String DD_VIEW_OTHERS_CH = "领取红包";
    private static final String DD_TIMER_BUTTON = "抢红包";
    private static final String DD_TIMER_BUTTON_BROTHER = "秒后关闭";
    private static final String DD_HONGBAO_PICK_ACTIVITY = "PickRedPacketsActivity";
    private static final String DD_HONGBAO_DETAIL_ACTIVITY = "RedPacketsDetailActivity";
    private static final String DD_GENERAL_ACTIVITY = "HomeActivity";
    private static final String DD_CHAT_ACTIVITY = "ChatMsgActivity";
    private String mCurrentActivityName = DD_GENERAL_ACTIVITY;

    private AccessibilityNodeInfo rootNodeInfo;
    private AccessibilityNodeInfo mReceiveNode;
    private AccessibilityNodeInfo mUnpackNode;
    private boolean mLuckyMoneyPicked;
    private boolean mLuckyMoneyReceived;
    private boolean mMutex = false;
    private boolean mChatMutex = false;

    private HongBaoSignature signature = new HongBaoSignature();

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
        Log.d(TAG, "mLuckyMoneyReceived:" + mLuckyMoneyReceived + " ,mLuckyMoneyPicked:" + mLuckyMoneyPicked);
        if (mLuckyMoneyReceived && !mLuckyMoneyPicked && (mReceiveNode != null)) {
            Log.d(TAG, "点击红包，打开拆红包页面");
            mMutex = true;
            mReceiveNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
            mLuckyMoneyReceived = false;
            mLuckyMoneyPicked = true;
        }
        /* 如果戳开但还未领取 */
        if (mUnpackCount == 1 && (mUnpackNode != null)) {
            int delayFlag = 200;
            new android.os.Handler().postDelayed(
                    new Runnable() {
                        public void run() {
                            try {
                                openPacket();
                            } catch (Exception e) {
                                Log.d(TAG, "Exception e:" + e);
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

        /* 聊天会话窗口 */
        if (mCurrentActivityName.contains(DD_CHAT_ACTIVITY)) {
            /* 遍历节点匹配定时红包的"抢红包"和"x秒后关闭" */
            AccessibilityNodeInfo timerNode = getTheLastNode(DD_TIMER_BUTTON);
            AccessibilityNodeInfo timerNode2 = getTheLastNode(DD_TIMER_BUTTON_BROTHER);
            Log.d(TAG, "抢红包node:" + timerNode + " ,node2:" + timerNode2);
            if (timerNode != null && timerNode2 != null) {
                mLuckyMoneyReceived = true;
                mReceiveNode = timerNode;
                Log.d(TAG, "准备从-定时红包-打开拆红包页面");
                return;
            }
            /* 遍历节点匹配“领取红包”和"查看红包" */
            AccessibilityNodeInfo node1 = this.getTheLastNode(DD_VIEW_OTHERS_CH, DD_VIEW_SELF_CH);
            Log.d(TAG, "查看红包node:" + node1);
            if (node1 != null) {
                if (this.signature.generateSignature(node1)) {
                    mLuckyMoneyReceived = true;
                    mReceiveNode = node1;
                    Log.d(TAG, "准备从-聊天列表-打开拆红包页面");
                }
                return;
            }
        }
        boolean hasValideNodes = true;
        if (mCurrentActivityName.contains(DD_HONGBAO_PICK_ACTIVITY)) {
            /* 戳开红包，红包已被抢完，遍历节点匹配“红包详情”和“手慢了” */
            hasValideNodes = this.hasOneOfThoseNodes(DD_HAND_SLOWLY, DD_LOOK_OTHERS_LUCK, DD_EXPIRES);
            Log.d(TAG, "是否已抢完 | 过期：" + hasValideNodes);

            /* 戳开红包，红包还没抢完，遍历节点匹配“拆红包” */
            AccessibilityNodeInfo node2 = findOpenButton(this.rootNodeInfo);
            Log.d(TAG, "拆 按钮：" + node2);
            if (node2 != null && "android.widget.ImageButton".equals(node2.getClassName())
                    && mCurrentActivityName.contains(DD_HONGBAO_PICK_ACTIVITY)) {
                mUnpackNode = node2;
                mUnpackCount += 1;
                return;
            }
        }
        Log.d(TAG, "mMutex:" + mMutex);
        if (mMutex && eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && (mCurrentActivityName.contains(DD_HONGBAO_DETAIL_ACTIVITY)
                || (hasValideNodes && mCurrentActivityName.contains(DD_HONGBAO_PICK_ACTIVITY)))) {
            mMutex = false;
            mLuckyMoneyPicked = false;
            mUnpackCount = 0;
            performGlobalAction(GLOBAL_ACTION_BACK);
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
            Log.d(TAG, "className:" + node.getClassName());
            if ("android.widget.ImageButton".equals(node.getClassName())) {
                return node;
            } else {
                return null;
            }
        }
        //layout元素，遍历找button
        AccessibilityNodeInfo button;
        for (int i = 0; i < node.getChildCount(); i++) {
            button = findOpenButton(node.getChild(i));
            if (button != null) {
                return button;
            }
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
//        mUnpackNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Log.d(TAG, "抢红包！！！");
//
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float dpi = metrics.densityDpi;
        Log.d(TAG, "density:" + dpi);
//        if (android.os.Build.VERSION.SDK_INT <= 23) {
//            mUnpackNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//        } else {
        if (android.os.Build.VERSION.SDK_INT > 23) {

            Path path = new Path();
            if (640 == dpi) {
                path.moveTo(780, 1925);
            } else {
                path.moveTo(700, 1800);
            }
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription gestureDescription = builder.addStroke(new GestureDescription.StrokeDescription(path, 10, 20)).build();
            dispatchGesture(gestureDescription, new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    Log.d(TAG, "onCompleted");
                    mMutex = true;
                    super.onCompleted(gestureDescription);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    Log.d(TAG, "onCancelled");
                    mMutex = true;
                    super.onCancelled(gestureDescription);
                }
            }, null);

        }
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
