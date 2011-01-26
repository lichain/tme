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
    char exName[100][100];
    int exg_count;
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

int mountExchange(int isSink, int sessId, const char *exName){
    FILE* fp=NULL;
    char cmd[100];
    
    // determine if it's a topic
    if(0==strncmp(exName,"topic:",6))
        snprintf(cmd,100,"mist-%s %d --mount %s --topic", (isSink?"sink":"source"), sessId, &exName[6]);
    else
        snprintf(cmd,100,"mist-%s %d --mount %s", (isSink?"sink":"source"), sessId, exName);
		
    printf("DEBUG: %s\n",cmd); //DEBUG
    fp=popen(cmd,"r");
    
    return pclose(fp);
}

void* consumer(void* p){
    param prm=*(param*)p;

    char fnbuf[100];
    sprintf(fnbuf,"t_multi_source.%s.out.%d",prm.exName[0],prm.idx); //out file's naming only contain exg1
    FILE* outfp=fopen(fnbuf,"w");

    char* buf=NULL;
    int sessid=createSession();
    if(sessid<0){
        error=1;
        pthread_exit(NULL);
    }
    sessArray[prm.idx]=sessid;
    for(int i=0;i < prm.exg_count;i++)
    {
        if(mountExchange(prm.isSink,sessid,prm.exName[i])!=0){
            error=1;
            pthread_exit(NULL);
        }
        printf("DEBUG: exg mounted\n"); //DEBUG
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
        fprintf(stderr,"usage: %s [consumer count] [exchange name 1] [exchange name 2] ...\n", argv[0]);
		fprintf(stderr,"\t[exchange name=topic:xxx if it's a topic, default is queue]\n");
        exit(-1);
    }
    int cons=atoi(argv[1]);
    
    sessArray=(int*)malloc(sizeof(int)*cons);
    //get all exchange name into array
    int exg_start_from = 2;
    int exg_count = argc - exg_start_from;
    char exgName[100][100];
    for(int i=0;i < exg_count;i++)
        strcpy(exgName[i],argv[exg_start_from+i]);
    /*DEBUG: print out all params
    for(int j=0;j < exg_count;j++)
        printf("%s  \n", exgName[j]);
    */
    
    int i;
    pthread_t* th_conAry=(pthread_t*)malloc(sizeof(pthread_t)*cons);
    for(i=0;i<cons;i++){
        param* conprm=(param*)malloc(sizeof(param));
        conprm->isSink=0;
        for(int k=0;k < exg_count;k++)
            strcpy(conprm->exName[k],exgName[k]);
        conprm->idx=i;
        conprm->exg_count=exg_count;
        pthread_create(&(th_conAry[i]),NULL,consumer,(void*)conprm);
    }

    for(i=0;i<cons;i++){
        pthread_join(th_conAry[i],NULL);
    }

    if(error)
        exit(-1);
}
