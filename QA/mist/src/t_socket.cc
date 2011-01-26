#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <pthread.h>
#include <string.h>
#include <netinet/in.h>
#include <unistd.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <iostream>
#include <netinet/tcp.h>
#include "MistMessage.pb.h"
#include "GateTalk.pb.h"
using namespace com::trendmicro::mist::proto;

#define MIST_PORT 9498

typedef struct{
    int isSink;
    uint32_t size;
    char exName[100];
    int count;
    int idx;
    unsigned int interval;
}param;

int* cntArray;
int* sessArray;
int error=0;
int pros=0;
time_t start=0;

int connectTo(int port){
    int sock=socket(AF_INET,SOCK_STREAM,0);
    struct sockaddr_in sa;
    sa.sin_family=AF_INET;
    inet_aton("127.0.0.1",(struct in_addr *)&sa.sin_addr.s_addr);
    sa.sin_port=htons(port);

    if(connect(sock,(struct sockaddr*)&sa,sizeof(sa))<0){
        close(sock);
        return -1;
    }
    else
        return sock;
}

int sendRequest(const Command& req, Command& res){
    int sock;
    if((sock=connectTo(MIST_PORT))<0)
        return -1;

    uint32_t byteSize=htonl(req.ByteSize());
    write(sock,&byteSize,4);
    req.SerializeToFileDescriptor(sock);

    read(sock,&byteSize,4);
    if(res.ParseFromFileDescriptor(sock)){
        close(sock);
        return 0;
    }
    else{
        close(sock);
        return -1;
    }
}

int createSession(){
    int session_id=-1;

    Command cmd;
    Session* sessPtr=cmd.add_session();
    sessPtr->mutable_connection()->set_host_name("");
    sessPtr->mutable_connection()->set_host_port("");
    sessPtr->mutable_connection()->set_username("");
    sessPtr->mutable_connection()->set_password("");
    sessPtr->mutable_connection()->set_broker_type("");

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            session_id=atoi(res.response(0).context().c_str());

    return session_id;
}

int mountExchange(int isSink, int sessId, const char* exName){
    int result=-1;
    Command cmd;
    Client* clientPtr=cmd.add_client();
    clientPtr->set_session_id(sessId);
    clientPtr->mutable_channel()->set_name(exName);
    clientPtr->mutable_channel()->set_type(Channel::QUEUE);
    clientPtr->set_type(isSink?Client::PRODUCER:Client::CONSUMER);
    clientPtr->set_action(Client::MOUNT);

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            result=0;
    return result;
}

int destroySession(int sessId){
    int result=-1;
    Command cmd;
    Request* reqPtr=cmd.add_request();
    reqPtr->set_type(Request::SESSION_DESTROY);
    char buf[20];
    snprintf(buf,20,"%d",sessId);
    reqPtr->set_argument(buf);

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            result=0;
    return result;
}

int attachSession(int isSink,int sessId){
    int sock=-1;
    Command cmd;
    Command res;
    Request* reqPtr=cmd.add_request();
    reqPtr->set_type(Request::CLIENT_ATTACH);
    char buf[20];
    snprintf(buf,20,"%d",sessId);
    reqPtr->set_argument(buf);
    reqPtr->set_role(isSink?Request::SINK:Request::SOURCE);

    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            sock=connectTo(atoi(res.response(0).context().c_str()));
    int trueflag=1;
    setsockopt(sock,IPPROTO_TCP,TCP_NODELAY,&trueflag,sizeof(int));
    return sock;
}

int detachSession(int isSink,int sessId){
    int result=-1;
    Command cmd;
    Request* reqPtr=cmd.add_request();
    reqPtr->set_type(Request::CLIENT_DETACH);
    char buf[20];
    snprintf(buf,20,"%d",sessId);
    reqPtr->set_argument(buf);
    reqPtr->set_role(isSink?Request::SINK:Request::SOURCE);

    Command res;
    if(sendRequest(cmd,res)==0)
        if(res.response(0).success())
            result=0;
    return result;
}

void* producer(void* p){
    param prm=*(param*)p;
    printf("producer %d: created\n",prm.idx);
    uint32_t numElem=prm.size>>2;
    int sessid=createSession();
    if(sessid<0){
        error=1;
        pthread_exit(NULL);
    }
    printf("producer %d: session %d created\n",prm.idx,sessid);

    if(mountExchange(prm.isSink,sessid,prm.exName)!=0){
        error=1;
        pthread_exit(NULL);
    }
    printf("producer %d: session %d mounted\n",prm.idx,sessid);

    int sock=attachSession(prm.isSink,sessid);
    printf("producer %d: session %d attached\n",prm.idx,sessid);
    if(sock<0){
        error=1;
        pthread_exit(NULL);
    }

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
        write(sock,&byteSize,4);
        msg.SerializeToFileDescriptor(sock);

        read(sock,&byteSize,4);
        read(sock,buf,ntohl(byteSize));
        Response res;
        res.ParseFromArray(buf,ntohl(byteSize));
        if(!res.success()){
            printf("producer error!\n");
            error=1;
            break;
        }
        if(prm.interval>0)
            usleep(prm.interval*1000);
    }

    close(sock);
    destroySession(sessid);
    printf("producer #%d finished sending\n",prm.idx);
    pthread_exit(NULL);
    return NULL;
}

int read_all(int blocking_fd, char* buf, size_t size)
{
    size_t size_read = 0;
    while(size_read < size)
    {
        int n = read(blocking_fd, buf + size_read, size - size_read);
        if(n <= 0)
            return n;
        size_read += n;
    }
    
    return size_read;
}

void* consumer(void* p){
    param prm=*(param*)p;
    printf("consumer %d: created\n",prm.idx);
    char* buf=(char*)malloc(prm.size+200);
    int sessid=createSession();
    if(sessid<0){
        error=1;
        pthread_exit(NULL);
    }
    sessArray[prm.idx]=sessid;
    printf("consumer %d: session %d created\n",prm.idx,sessid);

    if(mountExchange(prm.isSink,sessid,prm.exName)!=0){
        error=1;
        pthread_exit(NULL);
    }
    printf("consumer %d: session %d mounted\n",prm.idx,sessid);

    int sock=attachSession(prm.isSink,sessid);
    if(sock<0){
        error=1;
        pthread_exit(NULL);
    }
    printf("consumer %d: session %d attached\n",prm.idx,sessid);
    
    Response ackResponse;
    ackResponse.set_success(true);

    for(;;){
        MessageBlock msg;
        uint32_t besize;
        if(read(sock,&besize,4)<=0)
            pthread_exit(NULL);
        
        int n=read_all(sock,buf,ntohl(besize));
        msg.ParseFromArray(buf,ntohl(besize));
                        
        uint32_t* intPtr=(uint32_t*)(msg.message().c_str());
        unsigned int seed=intPtr[0];
        int j;
        uint32_t numElem=(prm.size>>2)-1;
        for(j=1;j<=numElem;j++){
            if(intPtr[j]!=rand_r(&seed)){
                printf("ERROR!\n");
                error=1;
                close(sock);
                pthread_exit(NULL);
            }
        }
        cntArray[prm.idx]++;

        uint32_t ackSize=htonl(ackResponse.ByteSize());
        write(sock,&ackSize,4);
        ackResponse.SerializeToFileDescriptor(sock);
    }
    close(sock);
    pthread_exit(NULL);
    return NULL;
}


int main(int argc, char* argv[]){
    if(argc<7){
        printf("usage: %s [msg size] [msg count] [producer count] [consumer count] [exchange name] [send interval (msec)]\n", argv[0]);
        exit(-1);
    }
    int size=atoi(argv[1]);
    int count=atoi(argv[2]);
    pros=atoi(argv[3]);
    int cons=atoi(argv[4]);
    int interval=atoi(argv[6]);
    printf("program started\n");
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
        proprm->interval=interval;
        pthread_create(&th_proAry[i],NULL,producer,(void*)proprm);
    }

    int done=0;
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
        
        if(sum==count){
            done=1;
            break;
        }
    }
    
    printf("send and recv complete\n");
    long totalSec=time(NULL)-start;

    for(i=0;i<cons;i++){
        destroySession(sessArray[i]);
    }

    for(i=0;i<pros;i++)
        pthread_join(th_proAry[i],NULL);
    for(i=0;i<cons;i++){
        pthread_join(th_conAry[i],NULL);
        printf("consumer #%d: %d msgs\n",i,cntArray[i]);
    }
    printf("throughput ~= %.02f msgs/sec, %.02f kB/sec\n\n",(float)count/totalSec,(float)count*size/totalSec/1000);
    
    if(error && (!done))
        exit(-1);
    else
        exit(0);
}
