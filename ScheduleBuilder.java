package com.company;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;

class ScheduleBuilder {
    private double totalHours = 0;
    private double idealHoursPerDay;
    private int duration;
    private Vector<Zone> zonesByPriority = new Vector<>();
    private Vector<Zone> zonesByDates = new Vector<>();
    private int[] sortedZonesIndexes;

    ScheduleBuilder(){
        setTotalHours();
        this.duration = (int)Data.getTaskList().duration;
        idealHoursPerDay = totalHours/duration;
        constructZones();
        BalanceZones();
        constructOutput();
    }

    static private class Zone{
        int zoneOrderInTime;
        int zoneIndex;
        double imbalance;
        int duration;
        double hrsPerDay;
        boolean isBalanced = false;
        boolean isSorted = false;
        Vector<Connection> connections;
        Hashtable<String, Double> capacity = new Hashtable<>();
        Zone(){}
    }

    static private class Connection{
        int zoneIndex;
        int dateIndex;
        String taskName;
        double imbalanceDifference;
        double taskCapacity;
        Connection(int dateIndex, String taskName , double imbalanceDifference, double taskCapacity){
            this.dateIndex = dateIndex;
            this.taskName = taskName;
            this.imbalanceDifference = imbalanceDifference;
            this.taskCapacity = taskCapacity;
        }
    }

    private void setTotalHours(){
        for(int i = 0; i < Data.getTaskList().getSize(); i++){
            totalHours += Data.getTaskList().tasks.get(i).getHours();
        }
    }

    private void constructZones(){
        int jump;
        Date current;
        Zone zone;
        for(int i = 0; i < Data.getTaskList().criticalDays.size() - 1; i++){ //for each critical date
            zone = new Zone(); //make zone
            zone.zoneOrderInTime = i; //set order

            //zone duration
            current = Data.getTaskList().criticalDays.get(i);
            jump = Data.difference(Data.getTaskList().criticalDays.get(i), Data.getTaskList().criticalDays.get(i + 1)) -1;
            zone.duration = jump;

            //capacity of each task
            double totalAvgHours = 0;
            double avgHours;
            for(int j = 0; j < Data.getTaskList().getSize(); j++){
                if(Data.getTaskList().tasks.get(j).getStart().compareTo(current) <= 0 && Data.getTaskList().tasks.get(j).getDeadline().compareTo(current) >= 0){
                    avgHours = Data.getTaskList().tasks.get(j).getHrsPerDay();
                    zone.capacity.put(Data.getTaskList().tasks.get(j).getName(), avgHours*zone.duration);
                    totalAvgHours += avgHours;
                }
            }

            //total capacity
            BigDecimal bd = new BigDecimal(totalAvgHours).setScale(2, RoundingMode.HALF_EVEN);
            zone.hrsPerDay = bd.doubleValue();
            BigDecimal im = new BigDecimal((totalAvgHours - idealHoursPerDay)*zone.duration).setScale(2, RoundingMode.HALF_EVEN);
            zone.imbalance = im.doubleValue();


            //insert
            zonesByDates.add(zone);
        }

        //System.out.println("Connections: ");
        for(Zone z: zonesByDates){
            //System.out.println("   Zone " + (z.zoneOrderInTime + 1));
            //set connections for each zone
            z.connections = vectorOfConnections(z);
           // for(Connection c: z.connections){
              //  System.out.println("   " + c.taskName + ", " + (zonesByDates.get(c.dateIndex).zoneOrderInTime + 1));
            //}
            //construct list of zones sorted by priority
            insertZone(z, 0, zonesByPriority.size() - 1);
        }

        for(Zone z: zonesByPriority){
            for(Connection c: z.connections)
                c.zoneIndex = zonesByDates.get(c.dateIndex).zoneIndex;
        }
        //set indexes in prioritizes zones vector
        for(int i = 0; i < zonesByPriority.size(); i++){
            zonesByPriority.get(i).zoneIndex = i;
        }

        //System.out.println("Zones before balancing:");
        //printZones();
        //System.out.println('\n');
    }

    private void insertZone(Zone zone, int start, int end){
        if(start == end) {
            if(zone.connections.size() < zonesByPriority.get(start).connections.size()){ //insert if number of connections is smaller in zone to insert
                zonesByPriority.insertElementAt(zone, start);
            }else if(zone.connections.size() == zonesByPriority.get(start).connections.size()){//if number of connections is the same in both zones
                if (Math.abs(zone.imbalance) > Math.abs(zonesByPriority.get(start).imbalance)) { //sort by imbalance
                    zonesByPriority.insertElementAt(zone, start);
                } else {
                    zonesByPriority.insertElementAt(zone, start + 1);
                }
            }else{
                zonesByPriority.add(zone);
            }

        }else if(start > end){
            zonesByPriority.insertElementAt(zone, start);
        }else{
            int mid = (start+end)/2;

            if(zone.connections.size() == zonesByPriority.get(mid).connections.size()){ // if middle zone has the sam number of connections...
                if(Math.abs(zone.imbalance) == Math.abs(zonesByPriority.get(mid).imbalance))//...and imbalance - insert
                    zonesByPriority.insertElementAt(zone, mid);
                else if(Math.abs(zone.imbalance) > Math.abs(zonesByPriority.get(mid).imbalance))
                    insertZone(zone, start, mid);
                else
                    insertZone(zone, mid + 1, end);
            }else if(zone.connections.size() < zonesByPriority.get(mid).connections.size()){
                insertZone(zone, start, mid);
            }else{
                insertZone(zone, mid + 1, end);
            }
        }
    }

    private void BalanceZones(){
        for(Zone zone: zonesByPriority){
            MaximumTransferForOne(zone);
        }
        /*
        sortZones("imbalance");

        for(int i = 0; i < zonesByPriority.size(); i++){
            EqualizedTransferForOne(zonesByPriority.get(sortedZonesIndexes[i]));
        }

         */
    }

    private void MaximumTransferForOne(Zone zoneToBalance){
        //if already in balance
        if(zoneToBalance.imbalance == 0) {
            return;
        }

        double initialImbalance = zoneToBalance.imbalance;
        for(Connection connection: zoneToBalance.connections){
            if(zoneToBalance.imbalance == 0)
                break;

            //if connection has only one connection, skip
            if(zonesByDates.get(connection.dateIndex).connections.size() == 1){
                continue;
            }

            //if the same number of connection, equalizing transfer
            if(zonesByDates.get(connection.dateIndex).connections.size() == zoneToBalance.connections.size()){
                if(initialImbalance > 0){
                    EqualizedTransfer(zonesByDates.get(connection.dateIndex), zoneToBalance, connection.taskName);
                }else
                    EqualizedTransfer(zoneToBalance, zonesByDates.get(connection.dateIndex), connection.taskName);
            }

            //if connection has more connections, balance current
            if(zonesByDates.get(connection.dateIndex).connections.size() > zoneToBalance.connections.size()){
                if(initialImbalance > 0){
                    BalanceFrom(zonesByDates.get(connection.dateIndex), zoneToBalance, connection.taskName);
                }else
                    BalanceTo(zoneToBalance, zonesByDates.get(connection.dateIndex), connection.taskName);
            }

            //if connection has less connections, balance connection
            if(zonesByDates.get(connection.dateIndex).connections.size() < zoneToBalance.connections.size()){
                if(initialImbalance > 0){
                    BalanceTo(zonesByDates.get(connection.dateIndex), zoneToBalance, connection.taskName);
                }else
                    BalanceFrom(zoneToBalance, zonesByDates.get(connection.dateIndex), connection.taskName);
            }

            if(initialImbalance*zoneToBalance.imbalance <= 0){
                break;
            }
        }
        zoneToBalance.isBalanced = true;
    }

    private void BalanceTo(Zone to, Zone from, String task){
        if(from.capacity.get(task) <= 0)
            return;

        if(to.imbalance == 0)
            return;

        double valueToTransfer;
        if(-to.imbalance > from.capacity.get(task)){
            valueToTransfer = from.capacity.get(task);
        }else{
            valueToTransfer = -to.imbalance;
        }
        from.capacity.put(task, from.capacity.get(task) - valueToTransfer);
        from.imbalance -= valueToTransfer;
        to.capacity.put(task, to.capacity.get(task) + valueToTransfer);
        to.imbalance += valueToTransfer;

        //System.out.println("Balance to Transfer " + valueToTransfer + " from zone " + (from.zoneOrderInTime + 1) + " to zone " + (to.zoneOrderInTime + 1) + " via " + task + "\n");
    }

    private void BalanceFrom(Zone to, Zone from, String task){
        if(from.capacity.get(task) <= 0)
            return;

        if(from.imbalance == 0)
            return;

        double valueToTransfer;
        if(from.imbalance >= from.capacity.get(task)){
            valueToTransfer = from.capacity.get(task);
        }else{
            valueToTransfer = from.imbalance;
        }

        from.capacity.put(task, from.capacity.get(task) - valueToTransfer);
        from.imbalance -= valueToTransfer;
        to.capacity.put(task, to.capacity.get(task) + valueToTransfer);
        to.imbalance += valueToTransfer;

        //System.out.println("Balance from Transfer " + valueToTransfer + " from zone " + (from.zoneOrderInTime + 1) + " to zone " + (to.zoneOrderInTime + 1) + " via " + task + "\n");
    }

    private void EqualizedTransfer(Zone to, Zone from, String task){
        //if "from" zone doesn't have hours to transfer
        if(from.capacity.get(task) <= 0)
            return;

        double valueToTransfer;
        double idealImbalance = Math.abs((to.imbalance+from.imbalance)/2.0);
        double requiredCapacity = Math.abs(from.imbalance) - idealImbalance;

        if(from.capacity.get(task) >= requiredCapacity){
            valueToTransfer = requiredCapacity;
        }else{
            valueToTransfer = from.capacity.get(task);
        }

        from.capacity.put(task, from.capacity.get(task) - valueToTransfer);
        from.imbalance -= valueToTransfer;
        to.capacity.put(task, to.capacity.get(task) + valueToTransfer);
        to.imbalance += valueToTransfer;

        //System.out.println("Equalized Transfer " + valueToTransfer + " from zone " + (from.zoneOrderInTime + 1) + " to zone " + (to.zoneOrderInTime + 1) + " via " + task + "\n");
    }

    private Vector<Connection> vectorOfConnections(Zone zone){
        Vector<Connection> connections = new Vector<>();
        Set<String> tasks = zone.capacity.keySet(); //get list of tasks in current zone
        for(Zone fellowZone: zonesByDates){ // go through all zones
            if(zone == fellowZone) //cannot be connected to itself
                continue;
            double imbalanceDifference = Math.abs(zone.imbalance - fellowZone.imbalance);
            for(String task: tasks){ //go through each task in current zone
                if(fellowZone.capacity.containsKey(task)){ //if task is in both zones make new connection
                    //add to sorted vector
                    if(connections.size() == 0) //if no connections yet
                        connections.add(new Connection(fellowZone.zoneOrderInTime, task, imbalanceDifference, fellowZone.capacity.get(task)));
                    else { //if there is connection list
                        boolean inserted = false;
                        for (int i = 0; i < connections.size(); i++) { //go through connections list to insert sorted
                            if(imbalanceDifference == connections.get(i).imbalanceDifference) {//if zone imbalance difference in current connection and new connection are equal
                                if(fellowZone.capacity.get(task) >= connections.get(i).taskCapacity){ //if task capacity is bigger in fellow zone than in current connection
                                    connections.insertElementAt(new Connection(fellowZone.zoneOrderInTime, task, imbalanceDifference, fellowZone.capacity.get(task)), i);
                                    inserted = true;
                                    break;
                                }
                            }else if(imbalanceDifference > connections.get(i).imbalanceDifference){ //if zone imbalance difference in new connection is bigger than in current connection
                                connections.insertElementAt(new Connection(fellowZone.zoneOrderInTime, task, imbalanceDifference, fellowZone.capacity.get(task)), i);
                                inserted = true;
                                break;
                            }
                        } //end go through connections list
                        if(!inserted){
                            connections.add(new Connection(fellowZone.zoneOrderInTime, task, imbalanceDifference, fellowZone.capacity.get(task)));
                        }
                    } //end if there is connection list
                } //end make new connection
            } //end go through each task in current zone
        }//end go through zones
        return connections;
    }

    private void constructOutput(){

        BuiltDayList bdList = new BuiltDayList();
        BuiltDay day;
        BuiltTask task;
        int pastDurations = 0;
        Set<String> tasks;
        for(Zone z: zonesByDates){
            for(int i = 0; i< z.duration; i++){
                day = new BuiltDay(new Date(Data.getTaskList().earliest.getTime() + TimeUnit.DAYS.toMillis(pastDurations + i)));
                tasks = z.capacity.keySet();
                for(String t: tasks){
                    double percentage = 0;
                    for(int j = 0; j < Data.getTaskList().getSize(); j++){
                        if(Data.getTaskList().tasks.get(j).getName() == t){
                            percentage = z.capacity.get(t)/Data.getTaskList().tasks.get(j).getHours();
                        }
                    }
                    task = new BuiltTask(t, z.capacity.get(t)/z.duration, percentage);
                    day.addBTask(task);
                }
                bdList.addBDay(day);
            }
            pastDurations += z.duration;
        }

        Data.setBuiltDaysList(bdList);
    }

    void printZones(){
        System.out.println("Total hours: "+ totalHours);
        System.out.println("Duration: "+ duration);
        System.out.println("Ideal Hours per day: "+ idealHoursPerDay + '\n');
        System.out.println("\n");
        for(Zone zone: zonesByDates){
            System.out.println("  zone " + (zone.zoneOrderInTime + 1) + ": duration = " + zone.duration);
            System.out.println("  Index = " + zone.zoneIndex);
            System.out.println("  Hours per day: " + zone.hrsPerDay);
            System.out.println("  Imbalance:  " + String.format("%.2f",zone.imbalance));
            System.out.print("   ");
            System.out.println(zone.capacity);
            System.out.println('\n');
        }/*
        System.out.println('\n');
        System.out.println("Zones by Priority:");
        for(Zone zone: zonesByPriority){
            System.out.println("zone " + (zone.zoneOrderInTime + 1) + ": duration = " + zone.duration);
            System.out.println("Index = " + zone.zoneIndex);
            System.out.println("   Hours per day: " + zone.hrsPerDay);
            System.out.println("   Imbalance:  " + zone.imbalance);
            System.out.print("   ");
            System.out.println(zone.capacity );
            System.out.println('\n');
        }
        */
    }
    private void sortZones(String method){
        sortedZonesIndexes = new int[zonesByPriority.size()];
        System.out.println("SORT:");
        if(method.equals("imbalance")){
            for(int i = 0; i < zonesByPriority.size(); i++){
                int max = 0;
                for(int k = 0; k < zonesByPriority.size(); k++){
                    if(!zonesByPriority.get(k).isSorted) {
                        max = k;
                        break;
                    }
                }

                for(int j = 0; j < zonesByPriority.size(); j++){ //find index of most balanced zone left

                    double a = zonesByPriority.get(j).imbalance;
                    double b = zonesByPriority.get(max).imbalance;
                    if(!zonesByPriority.get(j).isSorted && Math.abs(zonesByPriority.get(j).imbalance) > Math.abs(zonesByPriority.get(max).imbalance)){
                        max = j;
                    }
                }
                zonesByPriority.get(max).isSorted = true;
                sortedZonesIndexes[i] = max;
                System.out.println(zonesByPriority.get(max).zoneOrderInTime + 1);
            }
        }
    }
    private void EqualizedTransferForOne(Zone zoneToBalance) {

        double initialImbalance = zoneToBalance.imbalance;
        for(Connection connection: zoneToBalance.connections){

            if(zoneToBalance.imbalance > 0){
                EqualizedTransfer(zonesByPriority.get(connection.zoneIndex), zoneToBalance, connection.taskName);
            }else{
                EqualizedTransfer(zoneToBalance, zonesByPriority.get(connection.zoneIndex), connection.taskName);
            }

            if(initialImbalance*zoneToBalance.imbalance <= 0){
                break;
            }
        }
    }
}