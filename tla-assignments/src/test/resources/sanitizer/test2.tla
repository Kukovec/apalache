---------------------- MODULE test2 ----------------------
VARIABLE a,b

IsTwo(c) == c = 2

Next == /\ \/ /\ a' = b'
              /\ b' = a'
           \/ IsTwo(b')
        /\ a' = 1

       
Init == /\ a = 0
        /\ b = 0
        
Spec == [][Next]_<< a,b >>     
==============================================================

