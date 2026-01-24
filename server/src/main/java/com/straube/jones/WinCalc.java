package com.straube.jones;


public class WinCalc
{
    public static void main(String[] args)
    {

        Double Startcapital = 40_000.0;
        Double capital = Startcapital;
        for (int i = 0; i < 24; i++ )
        {
            double win = capital * 0.05;
            capital += win - win * 0.25; // minus tax
            System.out.println("after " + (i + 1) + ": " + String.format("%.2f", capital));
        }
        System.out.println("Capital - after tax: " + String.format("%.2f", capital));
        System.out.println("Prozentsatz: "
                        + String.format("%.2f", (capital - Startcapital) / Startcapital * 100)
                        + "%");

        // capital = Startcapital;
        // for (int i = 0; i < 24; i++ )
        // {
        //     double win = capital * 0.1;
        //     capital += win; // without tax
        //     System.out.println("Year " + (i + 1) + ": " + String.format("%.2f", capital));
        // }
        // System.out.println("Capital - before tax: " + String.format("%.2f", capital));
        // capital = capital - Startcapital - capital * 0.25;
        // System.out.println("Capital - after tax: " + String.format("%.2f", capital));
        // System.out.println("Prozentsatz: "
        //                 + String.format("%.2f", (capital - Startcapital) / Startcapital * 100)
        //                 + "%");

        // Double capital1 = Startcapital;
        // Double capital2 = Startcapital;
        // for (int i = 0; i < 10; i++ )
        // {
        //     Double win = Startcapital * 0.1; // 10% pro Iteration
        //     capital1 += win - win * 0.25;
        //     capital2 += win;
        //     System.out.println("Iteration " + (i + 1)
        //                     + ": "
        //                     + String.format("%.2f", capital1)
        //                     + " / "
        //                     + String.format("%.2f", capital2));
        // }
        // System.out.println("Capital1 - after tax: " + String.format("%.2f", capital1));
        // System.out.println("Capital2 - after tax: " + String.format("%.2f", capital2 - (capital2 - Startcapital) * 0.25) );

    }


    private static double fnc_Stock1(int iteration, double startCapital)
    {
        double capital = startCapital + startCapital * 0.1 * iteration;
        return capital;
    }
}
