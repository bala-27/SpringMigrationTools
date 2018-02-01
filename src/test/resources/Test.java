package Test;

import javax.jws.Autowired;
import javax.jws.Service;
import javax.jws.WebService;

@Service
public class Test {

    /**
     * blasfdusadlfj
     */
    private String blueService;

    //variable
    public String purple = "purple";

    /*
    asdfsafasdlkjlkasjdg
     */


    //blub
    @Autowired(required = false)
    public static void main(String[] args) {
        String[] brgs = args;
    }

    @Autowired
    public void setPurple(String purple) {
        // comment
        this.purple = purple;
    }

    @Autowired
    public void setBlue(String blue) {
        blueService = blue;
    }

    private static class Abc {

        String abc;

        public String def;

        public void method1() {

        }

        /*
        soafsmfmeawgadsajgflödjgl
         */

        public int method2(int i, int j) {
            return i + j;
        }
    }
}
