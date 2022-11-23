import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class Test_Runner
{
    public static void main(String[] args)
	{
        for (int i = 0; i < Integer.parseInt(args[0]); ++i) {
            Result result = JUnitCore.runClasses(Test_String.class);
            for (Failure failure : result.getFailures()) {
               System.out.println(failure.toString());
					System.exit(-1);
            }
        }
    }
}
