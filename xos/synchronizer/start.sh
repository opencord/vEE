export XOS_DIR=/opt/xos
nohup python vee-synchronizer.py  -C $XOS_DIR/synchronizers/vee/vee_synchronizer_config > /dev/null 2>&1 &
