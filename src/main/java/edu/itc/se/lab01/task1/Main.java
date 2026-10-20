package edu.itc.se.lab01.task1;


public class Main {
    static class CountdownTask implements Runnable {
    @Override
    public void run() {
        Countdown.countdown(Thread.currentThread().getName());
        }
    }

    static public class Countdown extends Thread{
        public Countdown(String name){
            super(name);
        }
        public static void countdown(String name){
            try {
                for (int i = 3; i > 0; i--) {
                System.out.println(name+": "+i);
                Thread.sleep(200);
                }
                System.out.println(name+ "liftoff");
            } catch (Exception e) {
                System.out.println("the thread is interrupt2");
            }
            
        }
        @Override 
        public void run(){
            countdown(getName());
        }
    }



    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo  = new Countdown("apollo");
        Thread gemini  = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());
        Thread[] thread = {apollo,gemini,mercury,artemis}; 
        // TODO: start (or run) all four depending on 'sequential'
        for (Thread t : thread) {
                if (sequential) {
                    t.run();
                }else{
                    t.start();
                }
        }

        System.out.println("Mission control: all launched");
        // TODO: join all four
        for (Thread t : thread) {
            t.join();
        }
        System.out.println("Mission control: all landed");
        // TODO: print name, isVirtual(), getState() of each
        for (Thread th : thread) {
            System.out.printf("%-8s virtual=%-5b state=%s%n",th.getName(), th.isVirtual(), th.getState());
        }


    }
}