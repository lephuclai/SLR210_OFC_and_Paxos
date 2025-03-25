package demo;
import akka.actor.ActorRef;
import java.util.ArrayList;

public class ProcessesRef {
	public final ArrayList<ActorRef> references;

    public ProcessesRef(ArrayList<ActorRef> references) {
        this.references = references;
    }         
}
