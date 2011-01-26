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
using namespace com::trendmicro::mist::proto;

typedef struct{
    int isSink;
    uint32_t size;
    char exName[100];
    int count;
    int idx;
}param;

int* sessArray;
int error=0;

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

void* consumer(void* p){
    param prm=*(param*)p;

    char fnbuf[100];
    sprintf(fnbuf,"t_multi_source.out.%d",prm.idx);
    FILE* outfp=fopen(fnbuf,"w");

    char* buf=NULL;
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
    unsigned char md5[MD5_DIGEST_LENGTH];

    FILE* fp=NULL;
    fp = popen(cmd, "r");
    for(;;){
        uint32_t besize;
        if(fread(&besize,4,1,fp)==0)
            pthread_exit(NULL);

        uint32_t size=ntohl(besize);
        if(buf==NULL)
            buf=(char*)malloc(ntohl(besize)+200);

        fread(buf,1,size,fp);

        MessageBlock msg;
        msg.ParseFromArray(buf,size);

        MD5((unsigned char*)msg.message().c_str(),msg.message().length(),md5);
        int k;
        for(int k=0;k<MD5_DIGEST_LENGTH;k++)
            fprintf(outfp,"%02x",md5[k]);
        fprintf(outfp,"\n");
        fflush(outfp);
    }
    fclose(outfp);
    pclose(fp);
    free(buf);
    pthread_exit(NULL);
    return NULL;
}

int main(int argc, char* argv[]){
    if(argc<3){
        fprintf(stderr,"usage: %s [consumer count] [exchange name]\n", argv[0]);
        exit(-1);
    }
    int cons=atoi(argv[1]);

    sessArray=(int*)malloc(sizeof(int)*cons);

    int i;
    pthread_t* th_conAry=(pthread_t*)malloc(sizeof(pthread_t)*cons);
    for(i=0;i<cons;i++){
        param* conprm=(param*)malloc(sizeof(param));
        conprm->isSink=0;
        strcpy(conprm->exName,argv[2]);
        conprm->idx=i;
        pthread_create(&(th_conAry[i]),NULL,consumer,(void*)conprm);
    }

    for(i=0;i<cons;i++){
        pthread_join(th_conAry[i],NULL);
    }

    if(error)
        exit(-1);
}
