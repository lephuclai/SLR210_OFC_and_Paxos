package demo;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.*;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        int N = 3;
        int f = 1;
        int tle = 1000;

        final ActorSystem system = ActorSystem.create("System");
        system.log().info("System started with N = " + N );

        ArrayList<ActorRef> references = new ArrayList<>();

        //create processes/actors
        for (int i = 0; i < N; i++) {
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N), "" + i);
            references.add(a);
        }

        //send references to processes
        ProcessesRef m = new ProcessesRef(references);
        for (ActorRef actor : references) {
            actor.tell(m, ActorRef.noSender());
        }

        //randomly shuffle the processes in the list
        Collections.shuffle(references);

        //send crash message to first f processes
        for (int i = 0; i < f; i++) {
            references.get(i).tell(new CrashMessage(), ActorRef.noSender());
        }

        long start = System.currentTimeMillis();
        for (ActorRef actor : references) {
            actor.tell(new StartTimeMessage(start), ActorRef.noSender());
        }
        
        for (ActorRef actor : references) {
            actor.tell(new LaunchMessage(), ActorRef.noSender());
        }
        
        Thread.sleep(tle);

        //select the process with index f as the leader
        System.out.println("LEADER: p" + references.get(f).path().name());
        for (int i = 0; i < N; i++) {
        	//
            if (i == f){
            	continue;
            }
            references.get(i).tell(new HoldMessage(), ActorRef.noSender());

        }
        
        //Let them run for some time
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            system.terminate();
        }
    }

}
