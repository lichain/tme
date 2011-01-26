#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <pthread.h>
#include <string.h>
#include <netinet/in.h>
#include <unistd.h>
#include <signal.h>
#include "MistMessage.pb.h"
using namespace com::trendmicro::mist::proto;

typedef struct{
    int isSink;
    uint32_t size;
    char exName[100];
    int count;
    int idx;
}param;

int* cntArray;
int* sessArray;
int error=0;
int pros=0;
time_t start=0;

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
    start=time(NULL);

    uint32_t i;
    uint32_t realSize=numElem<<2;
    uint32_t* buf=(uint32_t*)malloc(realSize+100);
    int startIdx=prm.count/pros*prm.idx;
    int endIdx=(prm.idx==pros-1?prm.count:prm.count/pros*(prm.idx+1));
    for(i=startIdx;i<endIdx;i++){
        buf[0]=i;
	unsigned int state=i;
        int j;
        for(j=1;j<=numElem-1;j++)
            buf[j]=rand_r(&state);
        
        MessageBlock msg;
        msg.set_id(prm.exName);
        msg.set_message(buf,realSize);

        uint32_t byteSize=htonl(msg.ByteSize());
        fwrite(&byteSize,4,1,fp);
        msg.SerializeToArray(buf,msg.ByteSize());
        fwrite(buf,1,msg.ByteSize(),fp);

        fflush(fp);
    }
    pclose(fp);

    snprintf(cmd, 100,"mist-sink %d -d", sessid);
    system(cmd);
    snprintf(cmd, 100,"mist-session -d %d", sessid);
    system(cmd);
    pthread_exit(NULL);
    return NULL;
}

void* consumer(void* p){
    param prm=*(param*)p;
    char* buf=(char*)malloc(prm.size+100);
    int sessid=createSession();
    if(sessid<0){
        error=1;
        pthread_exit(NULL);
    }
    sessArray[prm.idx]=sessid;

    if(mountExchange(prm.isSink,sessid,prm.exName)!=0){
        error=1;
        pthread_exit(NULL);
    }

    char cmd[100];
    snprintf(cmd, 100,"mist-source %d --attach", sessid);

    FILE* fp=NULL;
    fp = popen(cmd, "r");
    for(;;){
        uint32_t besize;
        if(fread(&besize,4,1,fp)==0)
            pthread_exit(NULL);

        uint32_t size=ntohl(besize);
        fread(buf,1,size,fp);

        MessageBlock msg;
        msg.ParseFromArray(buf,size);
        uint32_t* intPtr=(uint32_t*)(msg.message().c_str());

        unsigned int seed=intPtr[0];
        int j;
        uint32_t numElem=(prm.size>>2)-1;
        for(j=1;j<=numElem;j++){
            if(intPtr[j]!=rand_r(&seed)){
	        printf("ERROR!\n");
                error=1;
                pthread_exit(NULL);
            }
        }
        cntArray[prm.idx]++;
    }
    pthread_exit(NULL);
    return NULL;
}

int main(int argc, char* argv[]){
    if(argc<6){
        printf("usage: %s [msg size] [msg count] [producer count] [consumer count] [exchange name]\n", argv[0]);
        exit(-1);
    }
    int size=atoi(argv[1]);
    int count=atoi(argv[2]);
    pros=atoi(argv[3]);
    int cons=atoi(argv[4]);

    cntArray=(int*)malloc(sizeof(int)*cons);
    memset(cntArray,0,sizeof(int)*cons);
    sessArray=(int*)malloc(sizeof(int)*cons);

    int i;
    pthread_t* th_conAry=(pthread_t*)malloc(sizeof(pthread_t)*cons);
    for(i=0;i<cons;i++){
        param* conprm=(param*)malloc(sizeof(param));
        conprm->isSink=0;
        conprm->size=size;
        strcpy(conprm->exName,argv[5]);
        conprm->count=count;
        conprm->idx=i;
        pthread_create(&(th_conAry[i]),NULL,consumer,(void*)conprm);
    }

    pthread_t* th_proAry=(pthread_t*)malloc(sizeof(pthread_t)*pros);
    for(i=0;i<pros;i++){
        param* proprm=(param*)malloc(sizeof(param));
        proprm->isSink=1;
        proprm->size=size;
        strcpy(proprm->exName,argv[5]);
        proprm->count=count;
        proprm->idx=i;
        pthread_create(&th_proAry[i],NULL,producer,(void*)proprm);
    }

    int progress=0;
    while(!error){
        usleep(500000);
        int sum=0;
        for(i=0;i<cons;i++)
            sum+=cntArray[i];
        if(sum*10/count>progress){
            progress=sum*10/count;
            printf("receiving message... %d%%\n",progress*10);
        }
        
        if(sum==count)
             break;
    }
    long totalSec=time(NULL)-start;

    char cmd[100];
    for(i=0;i<cons;i++){
        snprintf(cmd, 100,"mist-source %d -d", sessArray[i]);
        system(cmd);
        snprintf(cmd, 100,"mist-session -d %d", sessArray[i]);
        system(cmd);
    }

    for(i=0;i<pros;i++)
        pthread_join(th_proAry[i],NULL);
    for(i=0;i<cons;i++){
        pthread_join(th_conAry[i],NULL);
        printf("consumer #%d: %d msgs\n",i,cntArray[i]);
    }
    printf("throughput ~= %.02f msgs/sec, %.02f kB/sec\n",(float)count/totalSec,(float)count*size/totalSec/1000);

    if(error)
        exit(-1);
}
