package com.straube.jones;


public class WinCalc
{
    public static void main(String[] args)
    {

        Double Startcapital = 100000.0;
        Double capital = Startcapital;
        for (int i = 0; i < 24; i++ )
        {
            double win = capital * 0.1;
            capital += win - win * 0.25; // minus tax
            System.out.println("Year " + (i + 1) + ": " + String.format("%.2f", capital));
        }
        System.out.println("Prozentsatz: " + capital / Startcapital);
    }
}
