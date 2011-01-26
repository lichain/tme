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
using namespace com::trendmicro::spn::proto;

//TODO: [7.exg type] currently not working

using namespace com::trendmicro::mist::proto;

typedef struct{
    int isSink;
    uint32_t size;
    char exName[100];
    int count;
    int idx;
    unsigned int interval;
    int sink_type;
    //char exg_type[100];
}param;

int error=0;
int pros=0;

int createSession(){
    int session_id=-1;
    FILE* fp=NULL;
    fp=popen("mist-session","r");
    assert(fp);
    fscanf(fp,"%d",&session_id);
    pclose(fp);
    return session_id;
}

int mountExchange(int isSink, int sessId, const char* exName){
    FILE* fp=NULL;
    char cmd[100];
    // determine if it's a topic
    if(0==strncmp(exName,"topic:",6))
        snprintf(cmd,100,"mist-%s %d --mount %s --topic", (isSink?"sink":"source"), sessId, &exName[6]);
    else
        snprintf(cmd,100,"mist-%s %d --mount %s", (isSink?"sink":"source"), sessId, exName);
    fp=popen(cmd,"r");
    assert(fp);
    return pclose(fp);
}

void* producer(void* p){
    param prm=*(param*)p;
    uint32_t numElem=prm.size>>2;
    
    printf("creating session...\n");
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
    // convert to string
    snprintf(cmd, 100,"mist-sink %d --attach", sessid);
    FILE* fp=NULL;
    fp = popen(cmd, "w");

    char fnbuf[100];
    sprintf(fnbuf,"t_multi_sink.%s.out.%d",prm.exName,prm.idx);
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

        //printf("sending msg...\n");
        fflush(fp);
        if(prm.interval>0)
            usleep(prm.interval*1000);
        
        // recreate session
        if(1==prm.sink_type){
            printf("destroying session...\n");
            pclose(fp);
            fclose(outfp);
            snprintf(cmd, 100,"mist-sink %d -d", sessid);
            system(cmd);
            snprintf(cmd, 100,"mist-session -d %d", sessid);
            system(cmd);
            // recreate session for producer
            printf("creating session...\n");
            sessid=createSession();
            if(sessid<0){
                error=1;
                pthread_exit(NULL);
            }

            if(mountExchange(prm.isSink,sessid,prm.exName)!=0){
                error=1;
                pthread_exit(NULL);
            }
            // convert cmd to string and mist attach
            snprintf(cmd, 100,"mist-sink %d --attach", sessid);
            fp=NULL;
            fp = popen(cmd, "w");
            assert(fp);

            sprintf(fnbuf,"t_multi_sink.%s.out.%d",prm.exName,prm.idx);
            outfp=fopen(fnbuf,"a");
            assert(outfp);
        }
        // reattach session
        else if(2==prm.sink_type){
            printf("deattaching session...\n");
            pclose(fp);
            fclose(outfp);
            snprintf(cmd, 100,"mist-sink %d -d", sessid);
            system(cmd);
            printf("attaching session...\n");
            // convert cmd to string and mist attach
            snprintf(cmd, 100,"mist-sink %d --attach", sessid);
            fp=NULL;
            fp = popen(cmd, "w");
            sprintf(fnbuf,"t_multi_sink.%s.out.%d",prm.exName,prm.idx);
            outfp=fopen(fnbuf,"a");
        }
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
#include <mcheck.h>

int main(int argc, char* argv[]){
    if(argc<7){
        fprintf(stderr,"\nusage: %s [1.msg size] [2.total msg count] [3.producer count] [4.exchange name] [5.interval] [6.sink type]\n\n", argv[0]);
        //fprintf(stderr,"usage: %s [1.msg size] [2.total msg count] [3.producer count] [4.exchange name] [5.interval] [6.sink type] [7.exg type]", argv[0]);
        fprintf(stderr,"\t[4.exchange name=topic:xxx if it's a topic, default is queue]\n");
        fprintf(stderr,"\t[6.sink type=0:no-reattach|1:re-create session|2:re-attach session]\n");
        //fprintf(stderr,"[exg type=q:queue|t:topic]\n");
        exit(-1);
    }
    //for mtrace
    //setenv("MALLOC_TRACE" , "./log",1);
    //mtrace();
    int size=atoi(argv[1]);
    int count=atoi(argv[2]);
    pros=atoi(argv[3]);
    int sink_type=atoi(argv[6]);
    
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
        proprm->sink_type=sink_type;
        //strcpy(proprm->exg_type,argv[7]);
        pthread_create(&th_proAry[i],NULL,producer,(void*)proprm);
    }

    for(i=0;i<pros;i++)
        pthread_join(th_proAry[i],NULL);
    //for mtrace
    //muntrace();
    if(error)
        exit(-1);
}
