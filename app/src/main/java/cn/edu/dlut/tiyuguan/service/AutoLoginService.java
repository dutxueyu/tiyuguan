package cn.edu.dlut.tiyuguan.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.edu.dlut.tiyuguan.base.BaseAuth;
import cn.edu.dlut.tiyuguan.base.BaseMessage;
import cn.edu.dlut.tiyuguan.base.BaseService;
import cn.edu.dlut.tiyuguan.event.ExceptionErrorEvent;
import cn.edu.dlut.tiyuguan.event.NetworkErrorEvent;
import cn.edu.dlut.tiyuguan.event.LoginFailedEvent;
import cn.edu.dlut.tiyuguan.event.LoginSuccessEvent;
import cn.edu.dlut.tiyuguan.global.NameConstant;
import cn.edu.dlut.tiyuguan.model.User;
import cn.edu.dlut.tiyuguan.util.AppUtil;
import de.greenrobot.event.EventBus;

/**
 * Created by asus on 2015/10/6.
 */
public class AutoLoginService extends BaseService {
    public static String NAME = AutoLoginService.class.getName();

    //Thread Pool Executors
    private ExecutorService execService;
    private boolean isFirst = true;

    @Override
    public void onCreate() {
        super.onCreate();
        execService = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null || intent.getAction() == null){
            stopSelf();
        }

        if(intent.getAction().equals(NAME + BaseService.ACTION_START)){
            isFirst = true;
            startService();
        }
        if(intent.getAction().equals(NAME + BaseService.ACTION_STOP)){
            stopSelf();
        }
        return super.onStartCommand(intent, flags, startId);

    }


    //begin login operation in background
    private void startService(){
        execService.execute(new Runnable() {
            @Override
            public void run() {
                SharedPreferences sp = AppUtil.getSharedPreferences(AutoLoginService.this);
                HashMap<String,String> urlParams = new HashMap<>();
                String nowtime = AppUtil.getTimeTag();

                urlParams.put("userId",sp.getString("username",null));
                urlParams.put("password",AppUtil.getSHA256(AppUtil.getSHA256(sp.getString("password",null)) + nowtime));
                urlParams.put("nowTime",nowtime);

                    //if user login
                    if(!AppUtil.isConnected(getApplicationContext())){
                        onNetworkError(new Exception("network is invalid！"));
                        return;
                    }
                    if(BaseAuth.isLogin()){
                        stopSelf();
                        return;
                    }
                    else{
                    //begin  remote login operation & query some important info about venus
                        try {
                            doTaskAsyn(NameConstant.task.login,NameConstant.api.login,urlParams);
                            Thread.sleep(30 * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
            }
        });
    }

    @Override
    public void onTaskCompleted(int taskId, String data) {
        /**login请求完成以后**/
        /**ToastUtil.showInfoToast(this,data);**/
        AppUtil.debugV("====AotoLogin Result:====",data);
        try {
            BaseMessage message = AppUtil.getMessage(data);
            if(message.isSuccessful()){
                User user = (User)message.getData("User");
                Log.v("TAG", "user对象\n:" + ((User) user).getUserName());
                //login success
                if((user).getUserName() != null){
                    BaseAuth.setUser(user);
                    BaseAuth.setLogin(true);

                    /**登录成功以后，看是否Record为空**/
                    if(BaseAuth.getUser().getRecordMap() == null){
                        AppUtil.debugV("====TAG====","AutoLoginService 正在启动QueryRecordService");
                        Intent intentQueryRecord = new Intent(this,QueryRecordService.class);
                        intentQueryRecord.setAction(QueryRecordService.NAME + BaseService.ACTION_START );

                        Date now = new Date(System.currentTimeMillis());
                        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
                        String endTime = format.format(now) + "240000";
                        String startTime = AppUtil.getBeforeTime(3,format,now) + "000000";
                        AppUtil.debugV("====TAG====","startTime:" + startTime + "endTime:" + endTime);
                        intentQueryRecord.putExtra("queryUrl",NameConstant.api.queryUserRecord + "?userId=" + BaseAuth.getUser().getUserId()+"&startTime=" + startTime + "&endTime=" + endTime);
                        startService(intentQueryRecord);
                    }
                    /**post loginsuccessevent  to who need this**/
                    LoginSuccessEvent event = new LoginSuccessEvent();
                    event.setEventDesc("login success!");
                    EventBus.getDefault().post(event);
                }
            }
            //login failed
            else{
                /**登录失败后，就设置记住密码为false**/
                SharedPreferences sp = AppUtil.getSharedPreferences(this);
                SharedPreferences.Editor editor = sp.edit();
                editor.putBoolean("rememberme", false);
                editor.commit();

                LoginFailedEvent event = new LoginFailedEvent();
                event.setEventDesc("login failed!");
                EventBus.getDefault().post(event);
            }
        } catch (Exception e) {
            onExceptionError(e);
        }
        /**stop when  task is completed**/
        stopSelf();
    }

}
