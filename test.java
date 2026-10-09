public class test {
    public static void main(String[] args) {
        for(java.lang.reflect.Field f : android.service.notification.NotificationListenerService.Ranking.class.getDeclaredFields()){
            System.out.println(f.getName());
        }
    }
}
