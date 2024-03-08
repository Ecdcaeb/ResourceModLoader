package mods.Hileb.rml.api.villagers.trades.ranges.utils;

import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * @Project CustomVillage
 * @Author Hileb
 * @Date 2023/8/20 9:57
 **/
public class PoissonDistribution{
    public static double getChance(int lambda,int r){
        double top1=Math.pow(Math.E,-lambda);
        double top2=Math.pow(lambda,r);
        double bottom= factorial(r);
        return ((top1*top2)/bottom);
    }
    public static int getRandomlyInRange(Random random,int lambda, int min, int max){
        // [min,max)
        final int length=max-min;
        double[] chances=new double[length];
        double all=0;
        for(int i=0;i<length;i++){
            int c=min+i;
            chances[i]=getChance(lambda,c);
            all+=chances[i];
        }
        double t=random.nextDouble();
        t=t*all;
        for(int i=0;i<length;i++){
            double chance=chances[i];
            if (t>chance){
                t-=chance;
                continue;
            }else {
                return i+min;
            }
        }
        return (min+max)/2;
    }
    public static int factorial(int a){
        if (a<0){
            throw new IllegalArgumentException("factorial error at:"+a);
        }
        else if (a==0 || a==1){
            return 1;
        }else {
            int sum=1;
            for(int i=1;i<=a;i++){
                sum*=i;
            }
            return sum;
        }
    }
}
