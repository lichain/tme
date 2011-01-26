#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <pthread.h>
#include <string.h>
#include <netinet/in.h>
#include <unistd.h>
#include <signal.h>
#include <openssl/md5.h>
#include "MistMessage.pb.h"
#include "SpnMessage.pb.h"
using namespace com::trendmicro::mist::proto;
using namespace com::trendmicro::spn::proto;

typedef struct{
    int isSink;
    uint32_t size;
    char exName[100];
    int count;
    int idx;
    unsigned int interval;
}param;

int error=0;
int pros=0;

int createSession(){
    int session_id=-1;
    FILE* fp=NULL;
    fp=popen("mist-session","r");
    fscanf(fp,"%d",&session_id);
    pclose(fp);
    return session_id;
}

int mountExchange(int isSink, int sessId, const char* exName){
    FILE* fp=NULL;
    char cmd[100];
    snprintf(cmd,100,"mist-%s %d --mount %s", (isSink?"sink":"source"), sessId, exName);
    fp=popen(cmd,"r");
    return pclose(fp);
}

void* producer(void* p){
    param prm=*(param*)p;
    uint32_t numElem=prm.size>>2;
    int sessid=createSession();
    if(sessid<0){
        error=1;
        pthread_exit(NULL);
    }

    if(mountExchange(prm.isSink,sessid,prm.exName)!=0){
        error=1;
        pthread_exit(NULL);
    }

    char cmd[100];
    snprintf(cmd, 100,"mist-sink %d --attach", sessid);
    FILE* fp=NULL;
    fp = popen(cmd, "w");

    char fnbuf[100];
    sprintf(fnbuf,"t_multi_sink.out.%d",prm.idx);
    FILE* outfp=fopen(fnbuf,"w");

    uint32_t i;
    uint32_t realSize=numElem<<2;
    uint32_t* buf=(uint32_t*)malloc(realSize+100);
    int startIdx=prm.count/pros*prm.idx;
    int endIdx=(prm.idx==pros-1?prm.count:prm.count/pros*(prm.idx+1));

    unsigned char md5[MD5_DIGEST_LENGTH];

    char* buf2=NULL;
    for(i=startIdx;i<endIdx;i++){
        buf[0]=i;
	    unsigned int state=i;
        int j;
        for(j=1;j<=numElem-1;j++)
            buf[j]=rand_r(&state);
            
        Container container;
        container.mutable_container_base()->mutable_message_list()->add_messages()->set_derived(buf,realSize);
        container.mutable_container_base()->mutable_message_list()->mutable_messages(0)->mutable_msg_base()->set_subject("");
        if(buf2==NULL)
            buf2=(char*)malloc(container.ByteSize());
        container.SerializeToArray(buf2,container.ByteSize());
        
        MD5((unsigned char*)buf2,container.ByteSize(),md5);
        int k;
        for(int k=0;k<MD5_DIGEST_LENGTH;k++)
            fprintf(outfp,"%02x",md5[k]);
        fprintf(outfp,"\n");
        fflush(outfp);

        MessageBlock msg;
        msg.set_id(prm.exName);
        msg.set_message(buf2,container.ByteSize());

        uint32_t byteSize=htonl(msg.ByteSize());
        fwrite(&byteSize,4,1,fp);
        msg.SerializeToArray(buf,msg.ByteSize());
        fwrite(buf,1,msg.ByteSize(),fp);

        fflush(fp);
        if(prm.interval>0)
            usleep(prm.interval*1000);
    }
    pclose(fp);
    fclose(outfp);

    snprintf(cmd, 100,"mist-sink %d -d", sessid);
    system(cmd);
    snprintf(cmd, 100,"mist-session -d %d", sessid);
    system(cmd);
    pthread_exit(NULL);
    return NULL;
}

int main(int argc, char* argv[]){
    if(argc<6){
        fprintf(stderr,"usage: %s [msg size] [total msg count] [producer count] [exchange name] [interval]\n", argv[0]);
        exit(-1);
    }
    int size=atoi(argv[1]);
    int count=atoi(argv[2]);
    pros=atoi(argv[3]);

    int i;
    pthread_t* th_proAry=(pthread_t*)malloc(sizeof(pthread_t)*pros);
    for(i=0;i<pros;i++){
        param* proprm=(param*)malloc(sizeof(param));
        proprm->isSink=1;
        proprm->size=size;
        strcpy(proprm->exName,argv[4]);
        proprm->count=count;
        proprm->idx=i;
        proprm->interval=atoi(argv[5]);
        pthread_create(&th_proAry[i],NULL,producer,(void*)proprm);
    }

    for(i=0;i<pros;i++)
        pthread_join(th_proAry[i],NULL);

    if(error)
        exit(-1);
}
