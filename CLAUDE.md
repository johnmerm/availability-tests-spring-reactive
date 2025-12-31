I want to build and test a booking system. 


The system shall allow customers to book tickets for events. So there is
* an events table with (id,name,max_tickets,num_shards).
* A ticket_type table (id,name)

* Since events may be recurrent there is an event_date table with (event_id,date,start_time). 

* For a given event_date there cannot be more than max_tickets sold in total. However it is possible to set limits per ticket_type in a table event_ticket_type with (event_id,ticket_type_id,max_per_tt,num_of_shards_per_tt). If you sum up the max_per_tt for a given event they may be greater than the max_tickets defined per event. (meaning for an event of 1000 seats you may define 900 normal and 200 discount tickets but only 1000 will be sold in total)

* There shall be a reservation table (id,event_id,date,start_time,payment_ref,created_at) and tickets (id,event_id,date,start_time,ticket_type,reservation_id) for tickets that have sucessfully been booked

In order to be able to give out availailities there shall be a table consumption_tt (event_id,date,start_time,ticket_type,shard_id,shard_current,shard_max). shard_id shall be a number between [0,num_shards) and the shard_max will be the limit specified by event_ticket_type table / num_shards. THis may be null if per-type limits have not been specified

* Also a consumption table (event_id,date,start_time,shard_id,shard_current,shard_max)


We need to expose the following endpoints
* Get /events which shall return the available events in the system. Pagination is required
* GET /events/{event_id} which shall return all dates & start_times and availabilities, both total and per_ticket_type. Pagination is required. Also it must be possible to set a date range for the search

* POST /events/{event_id}/{date}/{start_time} with payload [{tickettypeId:1,quantity:10}], which should attempt to create a reservation and related tickets. It shall return the reservation id. This ahppens in the beginning of the payment procedure, so the system will hold the tickets until the payment has completed

When a reservation request is posted, the shard_current in consumption and cosnumption_tt tables must be increased by the relevant quantitites. If limits have been specified and the shard_current becomes greater than the shard_max, the transaction shall be rolled back

* POST /reservation/{reservation_id} with body {paymentReference:124351234}. Which shall set the payment reference and thus finalize the transaction

If a reservation is left for more than 1 minute without been payed, it shall be deleted and counters restored

Sharding

We introduce sharding to minimize congestion in the DB. The idea is as follows

* Each event or event_tt define sthe number of shards there should be.
* For any given event or event_tt, the applications erver instance shall decide in which shard to send the request. Since it is possible that a request mya not fit in one shard, the application could attempt to split the reservation among shards
* We need to monitor the shards that have been exhausted so we don't send requests there


DB will be postgres
Application shall be spring boot reactive with JPA reactive (hibernate reactive?)
I will use redis as distributed memory between the application servers. Possibly we need tow layers of cache. One local to the app server and one in redis.
I want an simple SPA in Vue for UI testing of the solution 
I also want an artillery stress test so I can measure the trhoughput of the system