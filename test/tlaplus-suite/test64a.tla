----------------------- MODULE test64a -----------------------
VARIABLE x

ASet == {r \in  [{0} -> {0}]  : x}

Spec == x = TRUE /\ [][x' = (ASet = {})]_x
=============================================================
 